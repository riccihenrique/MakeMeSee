package com.riccihenrique.makemesee.view;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.SurfaceTexture;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.hardware.usb.UsbDevice;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.util.Log;
import android.view.Surface;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.riccihenrique.makemesee.dlib.FaceDet;
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
import org.opencv.android.Utils;
import org.opencv.core.Mat;
import org.opencv.core.MatOfRect;
import org.opencv.core.Rect;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.CascadeClassifier;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.List;
import java.util.Locale;
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

    private SpeechRecognizer speechRecognizer;
    final Intent speechRecognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);

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

    private USBMonitor.OnDeviceConnectListener mOnDeviceConnectListener;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_usbcamera);

        if (!OpenCVLoader.initDebug())
           System.exit(-1);

        nn = new NeuralNetwork(this);

        initUsbMonitor();
        initAcceleratorSensor();
        initSurfaces();
        initViews();
        initSpeechRecognizer();
        initDetections();
    }

    private void initUsbMonitor() {
        mOnDeviceConnectListener = new USBMonitor.OnDeviceConnectListener() {
            @Override
            public void onAttach(UsbDevice device) {
                if(mCameraLeft == null)
                    mUSBMonitor.requestPermission(mUSBMonitor.getDeviceList().get(0));

                else if(mCameraRight == null)
                    mUSBMonitor.requestPermission(mUSBMonitor.getDeviceList().get(0));
            }

            @Override
            public void onDettach(UsbDevice device) {
                if (mCameraLeft != null && device.equals(mCameraLeft.getDevice())) {
                    mCameraLeft.close();
                    if (mPreviewSurfaceLeft != null) {
                        mPreviewSurfaceLeft.release();
                        mPreviewSurfaceLeft = null;
                    }
                    mCameraLeft.destroy();
                    mCameraLeft = null;
                }
                else if (mCameraRight != null && device.equals(mCameraRight.getDevice())) {
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
                }
                else if (mCameraRight != null && device.equals(mCameraRight.getDevice())) {
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
        mUSBMonitor = new USBMonitor(this, mOnDeviceConnectListener);
    }

    private void initDetections() {
        Runnable runnableAI = () -> {
            while (true) {
                try {
                    if(/*canDetect*/mCameraRight != null || mCameraLeft != null) {

                        List<Bitmap> l = nn.recognize(mUVCCameraViewLeft.getBitmap(), mUVCCameraViewRight.getBitmap());
                        if(l.size() > 1) {
                            imgv.setImageBitmap(l.get(0));
                            imgv2.setImageBitmap(l.get(1));
                        }
                        Thread.sleep(2000);
                        canDetect = false;
                    }
                }
                catch (Exception e) { Log.e("Processar itens reconhecidos", e.getMessage()); }
            }
        };

        new Thread(runnableAI).start();
    }

    private void initSpeechRecognizer() {
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        speechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        speechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());

        speechRecognizer.setRecognitionListener(new RecognitionListener() {
            @Override
            public void onReadyForSpeech(Bundle bundle) {}

            @Override
            public void onBeginningOfSpeech() {}

            @Override
            public void onRmsChanged(float v) {}

            @Override
            public void onBufferReceived(byte[] bytes) {}

            @Override
            public void onEndOfSpeech() {}

            @Override
            public void onError(int i) {}

            @Override
            public void onResults(Bundle bundle) {
                String text = bundle.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).get(0);
                Log.e("VOZ", text);
                if(text.toLowerCase().startsWith("salvar")) {
                    Log.e("Voz", text);
                    try {
                        getPhotosToSave(text.split(" ")[1]);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }

            @Override
            public void onPartialResults(Bundle bundle) {}

            @Override
            public void onEvent(int i, Bundle bundle) {}
        });

        speechRecognizer.startListening(speechRecognizerIntent);
    }

    private void initAcceleratorSensor() {
        sensorManager = (SensorManager)getSystemService(SENSOR_SERVICE);
        sensorStep = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        mAccel = 0.00f;
        mAccelCurrent = SensorManager.GRAVITY_EARTH;
        mAccelLast = SensorManager.GRAVITY_EARTH;
    }

    private void initViews() {
        imgv = findViewById(R.id.imgv);
        imgv2 = findViewById(R.id.imgv2);
    }

    private void initSurfaces() {
        mUVCCameraViewLeft = findViewById(R.id.camera_view);
        mUVCCameraViewLeft.setAspectRatio(UVCCamera.DEFAULT_PREVIEW_WIDTH / (float)UVCCamera.DEFAULT_PREVIEW_HEIGHT);

        mUVCCameraViewRight = findViewById(R.id.camera_view2);
        mUVCCameraViewRight.setAspectRatio(UVCCamera.DEFAULT_PREVIEW_WIDTH / (float)UVCCamera.DEFAULT_PREVIEW_HEIGHT);
    }

    private String getCascadeFile() {
        try {
            InputStream is = getResources().openRawResource(R.raw.haarcascade);
            File cascadeDir = getDir("haarcascade", Context.MODE_PRIVATE);
            File mCascadeFile = new File(cascadeDir, "cascade.xml");
            FileOutputStream os = new FileOutputStream(mCascadeFile);
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = is.read(buffer)) != -1) {
                os.write(buffer, 0, bytesRead);
            }
            is.close();
            os.close();

            return mCascadeFile.getAbsolutePath();
        }
        catch (Exception e) {
            return "";
        }
    }

    private void getPhotosToSave(String name) throws Exception {
        CascadeClassifier cascadeClassifier = new CascadeClassifier(getCascadeFile());
        Rect rect;
        FaceDet faceDet = new FaceDet();
        int tentativas = 0;
        for (int i = 0; i < 5; i++) {
            Mat rgb = new Mat(), gray = new Mat();
            Bitmap bmp = mUVCCameraViewLeft.getBitmap();
            Utils.bitmapToMat(bmp, rgb);

            Imgproc.cvtColor(rgb, gray, Imgproc.COLOR_RGB2GRAY);

            MatOfRect matOfRect = new MatOfRect();
            cascadeClassifier.detectMultiScale(gray, matOfRect, 1,2,2,
                    new Size(300,300), new Size(300,300));
            Log.e("TESTE", matOfRect.toList().size() + "");
            if(matOfRect.total() == 0) {
                i -= 1;
                tentativas ++;
                if(tentativas >= 3)
                    throw new Exception("Deu ruim");
                continue;
            }
            else if(matOfRect.total() == 1) {
                rect = matOfRect.toList().get(0);
            }
            else {
                List<Rect> listRect = matOfRect.toList();
                rect = listRect.get(0);
                Rect rect_aux;
                for (int j = 1; j < listRect.size(); j++) {
                    rect_aux = listRect.get(j);

                    if((rect.width - rect.x) * (rect.height - rect.y) <
                    (rect_aux.width - rect_aux.x) * (rect_aux.height - rect_aux.y)) { // Find the closer face
                        rect = rect_aux;
                    }
                }
            }

            if(faceDet.train(Bitmap.createBitmap(bmp, rect.x, rect.y, rect.width, rect.height), name))
                continue;
            else
                i -= 1; // Only go to next image case the training success

            try { // Wait a little time to capture new face position
                Thread.sleep(200);
            }
            catch (Exception e) {}
        }
    }

    private void releaseUVCCamera(int id) {
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
        speechRecognizer.startListening(speechRecognizerIntent);

        mUSBMonitor.register();
        if (mCameraLeft != null)
            mCameraLeft.startPreview();

        if (mCameraRight != null)
            mCameraRight.startPreview();
    }

    @Override
    protected void onResume() {
        super.onResume();
        speechRecognizer.startListening(speechRecognizerIntent);

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
        speechRecognizer.stopListening();
        sensorManager.unregisterListener(this, sensorStep);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        speechRecognizer.destroy();

        if(mUSBMonitor != null){
            mUSBMonitor.destroy();
        }
        if (mCameraLeft != null)
            mCameraLeft.destroy();

        if (mCameraRight != null)
            mCameraRight.destroy();

        releaseUVCCamera(2);
    }

    @Override
    public USBMonitor getUSBMonitor() {return null;}

    @Override
    public void onDialogResult(boolean canceled) {}

    @Override
    public void onSurfaceCreated(CameraViewInterface view, Surface surface) {}

    @Override
    public void onSurfaceChanged(CameraViewInterface view, Surface surface, int width, int height) {}

    @Override
    public void onSurfaceDestroy(CameraViewInterface view, Surface surface) {}

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
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

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
