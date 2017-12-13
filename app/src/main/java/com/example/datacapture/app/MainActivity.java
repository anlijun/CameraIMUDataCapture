package com.example.datacapture.app;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.PixelFormat;
import android.hardware.Camera;
import android.hardware.Camera.PictureCallback;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;

public class MainActivity extends Activity {

    // ------------------------------

    private Camera mCamera;
    private CameraPreview mPreview;
    private SensorMonitor acc;

    // ---------- 参数设置 ----------

    public static final int maxImageNum = 50;      // 最多采集图像数量
    public static final int captureFPS = 1000;    // 每多少秒采集一张图片
    public static final int sensorCaptureFPS = 50;      // 每多少秒采集一次传感器数据

    private int imgWidth = 640;
    private int imgHeight = 480;

    // ---------- 布尔变量 ----------

    public boolean isCapturing = false;    // 记录相机是否在进行采集
    public boolean isSensorCapturing = false;    // 记录传感器是否在进行采集

    // ---------- 显示元素 ----------

    private Button buttonCapture, buttonStop, buttonSensorCapture, buttonSensorStop;
    private Button buttonCameraCapture, buttonCameraStop;
    public TextView accView;
    public TextView gyroView;
    public TextView cameraStatus;
    FrameLayout preview;

    // ------- 句柄以及定时器 -------

    private Handler handler;
    private Timer timerCaptureFPS;  // 控制采集帧率定时器
    private Timer timerStop;        // 控制停止定时器

    // ------- 计数器与时间戳 -------

    public int imageNum = 0;
    private long timestamp;
    private long startTimestamp;
    private long cameraCaptureStartTimestamp;
    private long cameraCaptureFinishTimestamp;
    private long onShutterTimestamp;

    // ------- 文件 ----------------

    private File imageInfoData;
    private FileOutputStream imgInfoFOS;

    private static File dataDir;


    // ---------- 其他变量 ----------

    private static final String TAG = "MyActivity";
//    private static final double EPSILON = 0.1f;
//    private static final float NS2S = 1.0f / 1000000000.0f;
//    private final float[] deltaRotationVector = new float[4];


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
                    || checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
                    || checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE,
                        Manifest.permission.CAMERA}, 1);
            } else {
            }
        }

        buttonCapture = (Button) findViewById(R.id.button_capture);
        buttonStop = (Button) findViewById(R.id.button_stop);
        buttonCameraCapture = (Button) findViewById(R.id.button_camera_capture);
        buttonCameraStop = (Button) findViewById(R.id.button_camera_stop);
        buttonSensorCapture = (Button) findViewById(R.id.button_sensor_capture);
        buttonSensorStop = (Button) findViewById(R.id.button_sensor_stop);

        accView = (TextView) findViewById(R.id.acc_xcoor);
        gyroView = (TextView) findViewById(R.id.gyro_xcoor);
        cameraStatus = (TextView) findViewById(R.id.camera_status);

        preview = (FrameLayout) findViewById(R.id.camera_preview);


        // Create的时候，首先让stop button不可用
        buttonStop.setEnabled(false);
        buttonSensorStop.setEnabled(false);
        buttonCameraStop.setEnabled(false);

        buttonCapture.setOnClickListener(new OnButtonClick());
        buttonStop.setOnClickListener(new OnButtonClick());
        buttonCameraCapture.setOnClickListener(new OnButtonClick());
        buttonCameraStop.setOnClickListener(new OnButtonClick());
        buttonSensorCapture.setOnClickListener(new OnButtonClick());
        buttonSensorStop.setOnClickListener(new OnButtonClick());

        handler = new Handler() {

            @Override
            public void handleMessage(Message msg) {
                if (msg.what == 0x123) {
                    takeOneShot();
                    imageNum++;
                } else if (msg.what == 0x124) {
                    stop();
                }
            }
        };


    }

    public class OnButtonClick implements View.OnClickListener {

        @Override
        public void onClick(View v) {
            switch (v.getId()) {
                case R.id.button_capture:

                    init();

                    startTimestamp = System.currentTimeMillis();

                    timerStop = new Timer();
                    timerStop.schedule(new stopThread(), new Date(), 500);

                    timerCaptureFPS = new Timer();
                    //timerCaptureFPS.schedule(new captureThread(), 1000, captureFPS);

                    Message msg= new Message();
                    msg.what=0x123;
                    handler.sendMessageDelayed(msg,100);//lijun

                    acc = new SensorMonitor(v.getContext(), accView, gyroView, startTimestamp, dataDir);
                    isSensorCapturing = true;
                    buttonSensorStop.setEnabled(true);

                    break;

                case R.id.button_stop:

                    stop();
                    isSensorCapturing = false;
                    acc.releaseSensor();

                    break;

                case R.id.button_camera_capture:

                    init();

                    startTimestamp = System.currentTimeMillis();

                    timerStop = new Timer();
                    timerStop.schedule(new stopThread(), new Date(), 500);

                    timerCaptureFPS = new Timer();
                    timerCaptureFPS.schedule(new captureThread(), 1000, captureFPS);

                    break;

                case R.id.button_camera_stop:

                    stop();
                    break;

                case R.id.button_sensor_capture:

                    acc = new SensorMonitor(v.getContext(), accView, gyroView, 0, null);
                    isSensorCapturing = true;
                    buttonSensorStop.setEnabled(true);
                    break;

                case R.id.button_sensor_stop:

                    isSensorCapturing = false;
                    acc.releaseSensor();
                    break;

            }
        }

    }

    class captureThread extends TimerTask {
        @Override
        public void run() {
            handler.sendEmptyMessage(0x123);
        }
    }

    class stopThread extends TimerTask {
        @Override
        public void run() {

            if (imageNum > maxImageNum) {
                handler.sendEmptyMessage(0x124);
                this.cancel();
            }
        }
    }


    private void init() {

        mCamera = getCameraInstance();

        // --------- Config Camera --------
        Camera.Parameters parameters = mCamera.getParameters();

        parameters.setPictureFormat(PixelFormat.JPEG);
        parameters.setPictureSize(imgWidth, imgHeight);

        try {
            parameters.setSceneMode(Camera.Parameters.SCENE_MODE_ACTION);
        } catch (Exception e) {
            Log.d(TAG, "Error starting action mode: " + e.getMessage());
        }

        parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_FIXED);
        mCamera.cancelAutoFocus();

        mCamera.setParameters(parameters);

        // ------------------

        createDataDir();

        imageInfoData = getImgInfoFile();

        try {
            imgInfoFOS = new FileOutputStream(imageInfoData);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        cameraStatus.setText("Camera Status : OK");

        mPreview = new CameraPreview(this, mCamera);
        preview.addView(mPreview);

        buttonStop.setEnabled(true);
        buttonCameraStop.setEnabled(true);

    }

    Camera.ShutterCallback mShutterCallback = new Camera.ShutterCallback() {
        @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
        @Override
        public void onShutter() {
            mCamera.enableShutterSound(true);
            onShutterTimestamp = System.currentTimeMillis() - startTimestamp;
        }
    };

    private static void createDataDir() {

        String dataFolder = new SimpleDateFormat("yyyyMMdd_HHmm").format(new Date());
        dataDir = new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_PICTURES), dataFolder);

        if (!dataDir.exists()) {
            if (!dataDir.mkdirs()) {
                Log.d("MyCameraApp", "failed to create image directory");
            }
        }
    }

    private static Boolean mFocused = false;
    private void takeOneShot() {

        buttonCapture.setEnabled(false);
        buttonCameraCapture.setEnabled(false);


        cameraCaptureStartTimestamp = System.currentTimeMillis() - startTimestamp;

        if (mCamera != null && !isCapturing) {
            try {

                Log.d(TAG,"takepicture0");
                mCamera.startPreview();
                if (!mFocused) {
                    mCamera.autoFocus(new Camera.AutoFocusCallback() {
                        @Override
                        public void onAutoFocus(boolean b, Camera camera) {
                            if (b) {
                                Log.i(TAG, "### onAutoFocus success ***************** ");
                                mCamera.cancelAutoFocus();
                            }
                        }
                    });
                    Thread.sleep(2000);
                    mFocused = true;
                }
            } catch (Exception e) {

                Log.d(TAG,"takepicture error1");
                Log.d(TAG, "Error starting camera preview: " + e.getMessage());
            }

            isCapturing = true;
            Log.d(TAG,"takepicture1");
            mCamera.takePicture(mShutterCallback, null, mPicture);
            Log.d(TAG,"takepicture2");

        }

        isCapturing = false;

        cameraCaptureFinishTimestamp = System.currentTimeMillis() - startTimestamp;

        long dT = cameraCaptureFinishTimestamp - cameraCaptureStartTimestamp;

        cameraStatus.setText("Camera Status : Image No." + imageNum + "\nCapture lasts for:" + dT + "ms" + "\n onShutterTime:" + onShutterTimestamp);

        try {
            imgInfoFOS.write((imageNum + " " + cameraCaptureStartTimestamp + " " + onShutterTimestamp + " " + cameraCaptureFinishTimestamp + "\n").getBytes());
            imgInfoFOS.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    private void stop() {

        try {
            imgInfoFOS.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        releaseCamera();
        cameraStatus.setText("End");
        timerCaptureFPS.cancel();

    }

    @Override
    protected void onPause() {
        super.onPause();

        if (isSensorCapturing) {
            acc.releaseSensor();
        }

        releaseCamera();              // release the camera immediately on pause event
    }

    public static Camera getCameraInstance() {
        Camera c = null;
        try {
            c = Camera.open(); // attempt to get a Camera instance
        } catch (Exception e) {
            // Camera is not available (in use or does not exist)
        }
        return c; // returns null if camera is unavailable
    }

    private PictureCallback mPicture = new PictureCallback() {

        @Override
        public void onPictureTaken(byte[] data, Camera camera) {

            Log.d(TAG,"takepicture3");
            File pictureFile = getOutputMediaFile();
            try {
                if (pictureFile == null) {
                    Log.d(TAG, "Error creating media file, check storage permissions: ");
                    return;
                }

                try {
                    FileOutputStream fos = new FileOutputStream(pictureFile);
                    fos.write(data);
                    fos.close();
                } catch (FileNotFoundException e) {
                    Log.d(TAG, "File not found: " + e.getMessage());
                } catch (IOException e) {
                    Log.d(TAG, "Error accessing file: " + e.getMessage());
                }
            }finally {

                Log.d(TAG,"takepicture4");
                handler.sendEmptyMessage(0x123);//lijun
            }
        }
    };


    /**
     * Create a File for saving an image or video
     */
    private File getOutputMediaFile() {
        // Create a media file name
        //String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmssSSS").format(new Date());
        String timeStamp = Long.toString(System.currentTimeMillis()) + "000000";
        File mediaFile;

        mediaFile = new File(dataDir.getPath() + File.separator +
                timeStamp + ".png");

        return mediaFile;
    }


    private void releaseCamera() {
        if (mCamera != null) {
            mCamera.stopPreview();
            mCamera.release();        // release the camera for other applications
            mCamera = null;
        }
    }

    private static File getImgInfoFile() {

        File imageInfoFile;

        imageInfoFile = new File(dataDir.getPath() + File.separator +
                "ImgInfo" + ".txt");

        return imageInfoFile;
    }

}











