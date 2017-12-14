package com.example.datacapture.app;

import android.annotation.TargetApi;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;


/**
 * Created by vanta_000 on 2014/4/30.
 */
public class SensorMonitor implements SensorEventListener{

    private Context context;

    private SensorManager mSensorManager;
    private Sensor mAccelerometer;
    private Sensor mGyroscope;
    private Sensor mRotationSensor;

    private TextView accText;
    private TextView gyroText;

    // 定义时间戳
    private long startTimestamp = 0l;   //  MainActivity里 init()结束后的系统时间
    // 下面几个时间戳均减去了startTimestamp
    private long accTimestamp       = 0l;   //  每次onSensorChanged()且Event == ACC时的系统时间
    private long gyroTimestamp      = 0l;   //  每次onSensorChanged()且Event == GYRO时的系统时间
    private long rotationTimestamp  = 0l;   //  每次onSensorChanged()且Event == RotationSensor的系统时间
    private long logTimestamp       = 0l;   //  每次往TXT文件中写数据的时间

    // 定义计数器
    private int count = 1;

    // 控制传感器帧率定时器
    private Timer timerSensorCapture;
    private Handler sensorHandler;


    private float[] accOutput                   = new float[3];     // 加速度计变量
    private float[] gyroOutput                  = new float[3];     // 陀螺仪变量
    private float[] rotationVectorQuaternion    = new float[4];     // 旋转向量四元数
    private float[] rotationVectorMatrix        = new float[9];     // 旋转向量矩阵

    // 文件
    private static File sensorDir = null;
    private FileOutputStream sensorFOS;
    private File sensorData;


    public SensorMonitor(Context context, TextView accText, TextView gyroText, long startTimestamp, File dataDir) { //, Handler mHandler, Runnable mUpdateTimeTask

        this.context  = context;
        this.accText  = accText;
        this.gyroText = gyroText;
        this.startTimestamp = startTimestamp;
        this.sensorDir = dataDir;

        initSensor();

        sensorHandler = new Handler() {

            @Override
            public void handleMessage(Message msg) {
                if(msg.what == 0x125){
                    saveSensorData();
                }
            }
        };
    }

    @TargetApi(Build.VERSION_CODES.GINGERBREAD)
    public void initSensor(){

        mSensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);

        mAccelerometer      = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        mGyroscope          = mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        mRotationSensor     = mSensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);

        mSensorManager.registerListener(this, mAccelerometer , SensorManager.SENSOR_DELAY_FASTEST);
        mSensorManager.registerListener(this, mGyroscope , SensorManager.SENSOR_DELAY_FASTEST);
        mSensorManager.registerListener(this, mRotationSensor, SensorManager.SENSOR_DELAY_FASTEST);

        timerSensorCapture = new Timer();
        timerSensorCapture.schedule(new sensorCaptureThread(), 500, MainActivity.sensorCaptureFPS);

        sensorData = getSensorFile();

        try{
            sensorFOS = new FileOutputStream(sensorData);
        } catch (FileNotFoundException e){
            e.printStackTrace();
        }

        // 如果没有从MainActivity传来startTimestamp,在此初始化
        if (startTimestamp == 0){
            startTimestamp = System.currentTimeMillis();
        }


    }

    class sensorCaptureThread extends TimerTask {
        @Override
        public void run() {
            sensorHandler.sendEmptyMessage(0x125);
        }
    }

    public void releaseSensor(){

        try{
            sensorFOS.close();
        } catch (IOException e){
            e.printStackTrace();
        }
        mSensorManager.unregisterListener(this);
        Toast.makeText(context, "Sensor Stopped..", Toast.LENGTH_SHORT).show();
        timerSensorCapture.cancel();
    }

    private void saveSensorData() {
        try {
            String timeStamp = Long.toString(System.currentTimeMillis()) + "000000";
            String tmp = timeStamp + "," + gyroOutput[0] + "," + gyroOutput[1] + "," + gyroOutput[2] + ","
                    + accOutput[0] + "," + accOutput[1] + "," + accOutput[2];
            sensorFOS.write((tmp + "\n").getBytes());
            sensorFOS.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void saveSensorData_old() {


        accText.setText("Output #" + count + "\n" + accOutput[0] + " " + accOutput[1] + " " + accOutput[2] + "\n@ time " + accTimestamp + "ms");
        gyroText.setText("Output #" + count + "\n" + gyroOutput[0] + " " + gyroOutput[1] + " " + gyroOutput[2] + "\n@ time " + gyroTimestamp + "ms");


        logTimestamp = System.currentTimeMillis() - startTimestamp;
        try{

            sensorFOS.write( (count + " ").getBytes() );
            sensorFOS.write( (accOutput[0] + " " + accOutput[1] + " " + accOutput[2] + " " +  accTimestamp + " ").getBytes() );
            sensorFOS.write( (gyroOutput[0] + " " + gyroOutput[1] + " " + gyroOutput[2] + " " + gyroTimestamp + " ").getBytes() );
            sensorFOS.write( (rotationVectorMatrix[0] + " " + rotationVectorMatrix[1] + " " + rotationVectorMatrix[2] + " ").getBytes() );
            sensorFOS.write( (rotationVectorMatrix[3] + " " + rotationVectorMatrix[4] + " " + rotationVectorMatrix[5] + " ").getBytes() );
            sensorFOS.write( (rotationVectorMatrix[6] + " " + rotationVectorMatrix[7] + " " + rotationVectorMatrix[8] + " ").getBytes() );
            sensorFOS.write( (rotationTimestamp + " " + logTimestamp + "\n").getBytes());

            sensorFOS.flush();

        } catch (IOException e){
            e.printStackTrace();
        }

        count++;
    }

    private static File getSensorFile(){

    //  类的创建方法直接获得MainActivity里建立的dataDir路径

        if(sensorDir == null)
        {
          String folderName = new SimpleDateFormat("yyyyMMdd_HHmm").format(new Date());
          sensorDir = new File("/sdcard/sensor_data/" + folderName);

        // 创建sensor存储目录

            if (!sensorDir.exists()){
                if (!sensorDir.mkdirs()){
                    Log.d("MyCameraApp", "failed to create sensor directory");
                    return null;}
            }

        }
//

        File sensorFile;

        sensorFile = new File(sensorDir.getPath() + File.separator + "imu0.csv");

        return sensorFile;
    }

    @TargetApi(Build.VERSION_CODES.GINGERBREAD)
    @Override
    public void onSensorChanged(SensorEvent event){

        if(event.sensor.getType()==Sensor.TYPE_ACCELEROMETER)
        {
            accTimestamp = System.currentTimeMillis() - startTimestamp;
            accOutput = event.values.clone();
        }

        if (event.sensor.getType() == Sensor.TYPE_GYROSCOPE)
        {

            gyroTimestamp = System.currentTimeMillis() - startTimestamp;
            gyroOutput = event.values.clone();

        }

        if (event.sensor.getType() == Sensor.TYPE_ROTATION_VECTOR)
        {

            rotationTimestamp = System.currentTimeMillis() - startTimestamp;
            SensorManager.getQuaternionFromVector(rotationVectorQuaternion,event.values);

            float temp;
            temp = rotationVectorQuaternion[0];

            rotationVectorQuaternion[0] = rotationVectorQuaternion[1];
            rotationVectorQuaternion[1] = rotationVectorQuaternion[2];
            rotationVectorQuaternion[2] = rotationVectorQuaternion[3];
            rotationVectorQuaternion[3] = temp;

            SensorManager.getRotationMatrixFromVector(rotationVectorMatrix,rotationVectorQuaternion);
        }

    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // can be safely ignored for this demo
    }

}
