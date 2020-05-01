package com.riccihenrique.makemesee.view;

import android.graphics.Bitmap;
import android.graphics.SurfaceTexture;
import android.hardware.usb.UsbDevice;
import android.os.Bundle;
import android.view.Surface;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;
import com.riccihenrique.makemesee.R;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import com.riccihenrique.makemesee.utils.Stereo;
import com.serenegiant.usb.CameraDialog;
import com.serenegiant.usb.USBMonitor;
import com.serenegiant.usb.UVCCamera;
import com.serenegiant.usb.widget.CameraViewInterface;
import com.serenegiant.usb.widget.UVCCameraTextureView;

import org.opencv.android.Utils;
import org.opencv.core.Mat;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class USBCameraActivity extends AppCompatActivity implements CameraDialog.CameraDialogParent, CameraViewInterface.Callback {
    private static final String TAG = "Debug";
    private static boolean DEBUG = true;

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
    private ImageView imgv;

    private int SELECTED_ID = -1;
    private Button btFoto;

    private USBMonitor.OnDeviceConnectListener mOnDeviceConnectListener = new USBMonitor.OnDeviceConnectListener() {
        @Override
        public void onAttach(UsbDevice device) {
            if(mCameraLeft == null)
            {
                mUSBMonitor.requestPermission(mUSBMonitor.getDeviceList().get(0));
                SELECTED_ID = 0;
            }

            else if(mCameraRight == null)
            {
                mUSBMonitor.requestPermission(mUSBMonitor.getDeviceList().get(0));
                SELECTED_ID = 1;
            }
        }

        @Override
        public void onDettach(UsbDevice device) {

        }

        @Override
        public void onConnect(UsbDevice device, USBMonitor.UsbControlBlock ctrlBlock, boolean createNew) {
            if(mCameraLeft != null && mCameraRight != null) return;

            final UVCCamera camera = new  UVCCamera();
            final int current_id = SELECTED_ID;
            EXECUTER.execute(new Runnable() {
                @Override
                public void run() {
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

        mUVCCameraViewLeft = (UVCCameraTextureView) findViewById(R.id.camera_view);
        mUVCCameraViewLeft.setAspectRatio(UVCCamera.DEFAULT_PREVIEW_WIDTH / (float)UVCCamera.DEFAULT_PREVIEW_HEIGHT);

        mUVCCameraViewRight = (UVCCameraTextureView) findViewById(R.id.camera_view2);
        mUVCCameraViewRight.setAspectRatio(UVCCamera.DEFAULT_PREVIEW_WIDTH / (float)UVCCamera.DEFAULT_PREVIEW_HEIGHT);

        imgv = (ImageView) findViewById(R.id.imgv);

        new Thread() {
            @Override
            public void run() {
                while (true) {
                    try
                    {
                        Mat left = new Mat();
                        Bitmap bmpleft = mUVCCameraViewLeft.getBitmap().copy(Bitmap.Config.ARGB_8888, true);
                        Utils.bitmapToMat(bmpleft, left);

                        Mat right = new Mat();
                        Bitmap bmpright = mUVCCameraViewRight.getBitmap().copy(Bitmap.Config.ARGB_8888, true);
                        Utils.bitmapToMat(bmpright, right);

                        Mat result = Stereo.getDisparityMap(left, right);
                        Bitmap res = Bitmap.createBitmap(bmpleft);
                        Utils.matToBitmap(result, res);
                        imgv.setImageBitmap(res);
                    }
                    catch (Exception e)
                    {
                        showShortMsg("Krai, não deu" + e.getMessage());
                    }
                }
            }
        }.start();

        btFoto = (Button) findViewById(R.id.btFoto);
        btFoto.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try
                {
                    /*Mat left = new Mat();
                    Bitmap bmpleft = mUVCCameraViewLeft.getBitmap().copy(Bitmap.Config.ARGB_8888, true);
                    Utils.bitmapToMat(bmpleft, left);

                    Mat right = new Mat();
                    Bitmap bmpright = mUVCCameraViewRight.getBitmap().copy(Bitmap.Config.ARGB_8888, true);
                    Utils.bitmapToMat(bmpright, right);

                    Mat result = Stereo.getDisparityMap(left, right);
                    Bitmap res = Bitmap.createBitmap(bmpleft);
                    Utils.matToBitmap(result, res);
                    imgv.setImageBitmap(res);*/
                }
                catch (Exception e)
                {
                    showShortMsg("Krai, não deu" + e.getMessage());
                }
            }
        });

        mUSBMonitor = new USBMonitor(this, mOnDeviceConnectListener);
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
    }

    @Override
    protected void onStop() {
        super.onStop();
        // step.3 unregister USB event broadcast
        /*if (mCameraHelper != null)
            mCameraHelper.unregisterUSB();

        if (mCameraHelper2 != null)
            mCameraHelper2.unregisterUSB();*/
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
}
