CameraIMUDataCapture
====================

This app is to capture image, as well as inertial sensor outputs on Android device.

For image capturing:

+ 1280*960 @ 1fps
+ Android SCENE_MODE_ACTION
+ Android FOCUS_MODE_FIXED

For inertial sensor output capturing:

+ Accelerometer output every 50ms
+ Gyroscope output every 50ms

-------------------------

All datas will be stored at /sdcard/Pictures/#yyyyMMdd_HHmm#/

data format:
/sdcard/Pictures/#yyyyMMdd_HHmm#/
IMG_20171208_222448527.jpg
IMG_20171208_222449313.jpg

ImgInfo.txt
  index StartTimestamp  ShutterTimestamp  FinishTimestamp
  0 1008 0 1009
  1 2002 1405 2003

Sensor.txt
count acc0  acc1  acc2  accTimestamp  gyro0 gyro1 gyro2 gyroTimestamp rotationVectorMatrix0--rotationVectorMatrix8 rotationTimestamp logTimestamp
1 -0.043110352 8.58854 4.7397437 523 0.083823875 -0.053404268 -0.042186577 523 0.38409117 -0.4619578 0.7994179 0.92329466 0.19306436 -0.33204386 -9.4884634E-4 0.86563337 0.50067747 516 531
