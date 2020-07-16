package com.riccihenrique.makemesee.view;

import android.graphics.Bitmap;
import android.graphics.SurfaceTexture;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.hardware.usb.UsbDevice;
import android.os.Bundle;
import android.view.Surface;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.riccihenrique.makemesee.utils.NeuralNetwork;
import com.serenegiant.usb.CameraDialog;
import com.serenegiant.usb.USBMonitor;
import com.serenegiant.usb.UVCCamera;
import com.serenegiant.usb.widget.CameraViewInterface;
import com.serenegiant.usb.widget.UVCCameraTextureView;
import com.riccihenrique.makemesee.R;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.opencv.android.OpenCVLoader;

import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class USBCameraActivity extends AppCompatActivity implements CameraDialog.CameraDialogParent, CameraViewInterface.Callback, SensorEventListener {
    // for thread pool
    private static final int CORE_POOL_SIZE = 1;		// initial/minimum threads
    private static final int MAX_POOL_SIZE = 4;			// maximum threads
    private static final int KEEP_ALIVE_TIME = 10;		// time periods while keep the idle thread
    protected static final ThreadPoolExecutor EXECUTER
            = new ThreadPoolExecutor(CORE_POOL_SIZE, MAX_POOL_SIZE, KEEP_ALIVE_TIME,
            TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>());

    // for accessing USB and USB camera
    private USBMonitor mUSBMonitor;

    private UVCCamera mCameraLeft = null;
    private UVCCameraTextureView mUVCCameraViewLeft;
    private Surface mPreviewSurfaceLeft;

    private UVCCamera mCameraRight = null;
    private UVCCameraTextureView mUVCCameraViewRight;
    private Surface mPreviewSurfaceRight;

    private double[][] matRight, matLeft;
    private double[] distRight, dirtLeft;

    private ImageView imgv;
    private ImageView imgv2;
    private NeuralNetwork nn;

    private float[] mGravity;
    private float mAccel;
    private float mAccelCurrent;
    private float mAccelLast;

    private SensorManager sensorManager;
    private Sensor sensorStep;
    private boolean canDetect = true;

    private USBMonitor.OnDeviceConnectListener mOnDeviceConnectListener = new USBMonitor.OnDeviceConnectListener() {
        @Override
        public void onAttach(UsbDevice device) {
            if(mCameraLeft == null)
                mUSBMonitor.requestPermission(mUSBMonitor.getDeviceList().get(0));

            else if(mCameraRight == null)
                mUSBMonitor.requestPermission(mUSBMonitor.getDeviceList().get(0));
        }

        @Override
        public void onDettach(UsbDevice device) {
        }

        @Override
        public void onConnect(UsbDevice device, USBMonitor.UsbControlBlock ctrlBlock, boolean createNew) {
            if(mCameraLeft != null && mCameraRight != null) return;

            final UVCCamera camera = new  UVCCamera();
            EXECUTER.execute(() -> {
                camera.open(ctrlBlock);

                if (mCameraLeft == null) {
                    mCameraLeft = camera;
                    if (mPreviewSurfaceLeft != null) {
                        mPreviewSurfaceLeft.release();
                        mPreviewSurfaceLeft = null;
                    }

                    final SurfaceTexture st = mUVCCameraViewLeft.getSurfaceTexture();
                    if (st != null)
                        mPreviewSurfaceLeft = new Surface(st);

                    mCameraLeft.setPreviewDisplay(mPreviewSurfaceLeft);
                    mCameraLeft.startPreview();
                    mCameraLeft.setAutoFocus(false);
                } else

                if (mCameraRight == null) {
                    mCameraRight = camera;
                    if (mPreviewSurfaceRight != null) {
                        mPreviewSurfaceRight.release();
                        mPreviewSurfaceRight = null;
                    }

                    final SurfaceTexture st = mUVCCameraViewRight.getSurfaceTexture();
                    if (st != null)
                        mPreviewSurfaceRight = new Surface(st);

                    mCameraRight.setPreviewDisplay(mPreviewSurfaceRight);
                    mCameraRight.startPreview();
                }
            });
        }

        @Override
        public void onDisconnect(UsbDevice device, USBMonitor.UsbControlBlock ctrlBlock) {
            if (mCameraLeft != null && device.equals(mCameraLeft.getDevice())) {
                mCameraLeft.close();
                if (mPreviewSurfaceLeft != null) {
                    mPreviewSurfaceLeft.release();
                    mPreviewSurfaceLeft = null;
                }
                mCameraLeft.destroy();
                mCameraLeft = null;
            } else if (mCameraRight != null && device.equals(mCameraRight.getDevice())) {
                mCameraRight.close();
                if (mPreviewSurfaceRight != null) {
                    mPreviewSurfaceRight.release();
                    mPreviewSurfaceRight = null;
                }
                mCameraRight.destroy();
                mCameraRight = null;
            }
        }

        @Override
        public void onCancel(UsbDevice device) {
        }
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_usbcamera);

        if (!OpenCVLoader.initDebug())
           System.exit(-1);

        nn = new NeuralNetwork(this);

        sensorManager = (SensorManager)getSystemService(SENSOR_SERVICE);
        sensorStep = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        mAccel = 0.00f;
        mAccelCurrent = SensorManager.GRAVITY_EARTH;
        mAccelLast = SensorManager.GRAVITY_EARTH;

        mUVCCameraViewLeft = findViewById(R.id.camera_view);
        mUVCCameraViewLeft.setAspectRatio(UVCCamera.DEFAULT_PREVIEW_WIDTH / (float)UVCCamera.DEFAULT_PREVIEW_HEIGHT);

        mUVCCameraViewRight = findViewById(R.id.camera_view2);
        mUVCCameraViewRight.setAspectRatio(UVCCamera.DEFAULT_PREVIEW_WIDTH / (float)UVCCamera.DEFAULT_PREVIEW_HEIGHT);

        imgv = findViewById(R.id.imgv);
        imgv2 = findViewById(R.id.imgv2);

        mUSBMonitor = new USBMonitor(this, mOnDeviceConnectListener);

        Runnable runnable = () -> {
            while (true) {
                try {
                    if(/*canDetect*/mCameraRight != null) {

                        List<Bitmap> l = nn.recognize(mUVCCameraViewLeft.getBitmap(), mUVCCameraViewRight.getBitmap());
                        if(l.size() > 1) {
                            imgv.setImageBitmap(l.get(0));
                            imgv2.setImageBitmap(l.get(1));
                        }
                        Thread.sleep(2000);
                        canDetect = false;
                    }
                }
                catch (Exception e) { System.out.println(e.getMessage()); }
            }
        };

        new Thread(runnable).start();
    }

    private void releaseUVCCamera(int id){
        if(id == 0 || id == 2){
            mCameraLeft.close();

            if (mPreviewSurfaceLeft != null){
                mPreviewSurfaceLeft.release();
                mPreviewSurfaceLeft = null;
            }
            mCameraLeft.destroy();
            mCameraLeft = null;
        }
        if(id == 1 || id == 2){
            mCameraRight.close();

            if (mPreviewSurfaceRight != null){
                mPreviewSurfaceRight.release();
                mPreviewSurfaceRight = null;
            }
            mCameraRight.destroy();
            mCameraRight = null;
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        mUSBMonitor.register();
        if (mCameraLeft != null)
            mCameraLeft.startPreview();

        if (mCameraRight != null)
            mCameraRight.startPreview();
    }

    @Override
    protected void onResume() {
        super.onResume();

        mUSBMonitor.register();
        if (mCameraLeft != null)
            mCameraLeft.startPreview();

        if (mCameraRight != null)
            mCameraRight.startPreview();

        sensorManager.registerListener(this, sensorStep, SensorManager.SENSOR_DELAY_NORMAL);
    }

    @Override
    protected void onStop() {
        super.onStop();
        // step.3 unregister USB event broadcast
        sensorManager.unregisterListener(this, sensorStep);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if(mUSBMonitor != null){
            mUSBMonitor.destroy();
        }
        if (mCameraLeft != null)
            mCameraLeft.destroy();

        if (mCameraRight != null)
            mCameraRight.destroy();

        releaseUVCCamera(2);
    }

    private void showShortMsg(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    @Override
    public USBMonitor getUSBMonitor() {
        return null /*mCameraHelper.getUSBMonitor()*/;
    }

    @Override
    public void onDialogResult(boolean canceled) {
        if (canceled)
            showShortMsg("Canelar");
    }

    @Override
    public void onSurfaceCreated(CameraViewInterface view, Surface surface) {
        /*if (!isPreview && mCameraHelper.isCameraOpened()) {
            mCameraHelper.startPreview(mUVCCameraView);
            isPreview = true;
        }

        if (!isPreview2 && mCameraHelper2.isCameraOpened()) {
            mCameraHelper2.startPreview(mUVCCameraView2);
            isPreview2 = true;
        }*/
    }

    @Override
    public void onSurfaceChanged(CameraViewInterface view, Surface surface, int width, int height) {}

    @Override
    public void onSurfaceDestroy(CameraViewInterface view, Surface surface) {
        /*if (isPreview && mCameraHelper.isCameraOpened()) {
            mCameraHelper.stopPreview();
            isPreview = false;
        }

        if (isPreview2 && mCameraHelper2.isCameraOpened()) {
            mCameraHelper2.stopPreview();
            isPreview2 = false;*/
        }

    @Override
    public void onSensorChanged(SensorEvent event) {
            if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER){
                mGravity = event.values.clone();
                // Shake detection
                float x = mGravity[0];
                float y = mGravity[1];
                float z = mGravity[2];
                mAccelLast = mAccelCurrent;
                mAccelCurrent = (float) Math.sqrt(x*x + y*y + z*z);
                float delta = mAccelCurrent - mAccelLast;
                mAccel = mAccel * 0.9f + delta;
                // Make this higher or lower according to how much
                // motion you want to detect
                if(mAccel > 1){
                    /*Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
                    v.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE));*/
                    canDetect = true;
                }
            }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    private void readJson() {
        String leftFile = "left.json";
        String rightFile = "right.json";
        try {
            JSONObject reader = new JSONObject(leftFile);
            JSONArray mat = reader.getJSONArray("matrix");
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }
}
