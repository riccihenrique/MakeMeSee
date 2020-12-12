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
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.view.Surface;
import android.widget.ImageView;

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
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class USBCameraActivity extends AppCompatActivity implements CameraDialog.CameraDialogParent, CameraViewInterface.Callback, SensorEventListener, TextToSpeech.OnInitListener{
    // for thread pool
    private static final int CORE_POOL_SIZE = 1;		// initial/minimum threads
    private static final int MAX_POOL_SIZE = 4;			// maximum threads
    private static final int KEEP_ALIVE_TIME = 10;		// time periods while keep the idle thread
    protected static final ThreadPoolExecutor EXECUTER
            = new ThreadPoolExecutor(CORE_POOL_SIZE, MAX_POOL_SIZE, KEEP_ALIVE_TIME,
            TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>());

    private USBMonitor mUSBMonitor;

    private UVCCamera mCameraLeft = null;
    private UVCCameraTextureView mUVCCameraViewLeft;
    private Surface mPreviewSurfaceLeft;

    private UVCCamera mCameraRight = null;
    private UVCCameraTextureView mUVCCameraViewRight;
    private Surface mPreviewSurfaceRight;

    private SpeechRecognizer speechRecognizer;
    final Intent speechRecognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);

    private ImageView imgv;
    private ImageView imgv2;
    private NeuralNetwork nn;

    private float[] mGravity;
    private float mAccel;
    private float mAccelCurrent;
    private float mAccelLast;

    private int lastStep = 0;
    private int actualStep = 0;
    private boolean canDetect = true;

    private SensorManager sensorManager;
    private Sensor sensorStep;
    private boolean spoke1 = false;
    private boolean spoke2 = false;

    private int stepCount;
    private boolean toggle;
    private double prevY;
    private double threshold = 0.8;
    private boolean ignore;
    private int countdown;
    // Gravity for accelerometer data
    private float[] gravity = new float[3];
    // smoothed values
    private float[] smoothed = new float[3];
    // sensor gravity
    private Sensor sensorGravity;
    private double bearing = 0;
    private boolean canDetectVoice = false;

    private TextToSpeech txt2Speech;

    private USBMonitor.OnDeviceConnectListener mOnDeviceConnectListener;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_usbcamera);
        if (!OpenCVLoader.initDebug())
            System.exit(-1);

        initText2Speech();
        nn = new NeuralNetwork(this, txt2Speech);

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
                if(mCameraLeft == null) {
                    if(mUSBMonitor.getDeviceList().size() > 0)
                    {
                        mUSBMonitor.requestPermission(mUSBMonitor.getDeviceList().get(0));
                        if(!spoke1) {
                            txt2Speech.speak("Muito bem! Agora conecte a camera direita", TextToSpeech.QUEUE_ADD, null, "3");
                            spoke1 = true;
                        }
                    }
                }

                else if(mCameraRight == null) {
                    if(mUSBMonitor.getDeviceList().size() > 0) {
                        mUSBMonitor.requestPermission(mUSBMonitor.getDeviceList().get(0));
                        if(!spoke2) {
                            txt2Speech.speak("Isso aí Henrique. Para que eu te informe tudo o que estiver a menos de 7 metros de você e também algumas pessoas conhecidas basta dizer Reconhecer", TextToSpeech.QUEUE_ADD, null, "3");
                            spoke2 = true;
                        }
                    }
                }
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

    private void initText2Speech() {
        txt2Speech = new TextToSpeech(this, this);
    }

    private void initDetections() {
        AtomicBoolean flag = new AtomicBoolean(false);
        Runnable runnableAI = () -> {
            while (true) {
               try {
                    if(mCameraRight != null && mCameraLeft != null) {
                        flag.set(canDetect);
                        List<Object> l = nn.recognize(mUVCCameraViewLeft.getBitmap(), mUVCCameraViewRight.getBitmap(), canDetect);
                        //flag.set(canDetect);
                        imgv.setImageBitmap((Bitmap) l.get(0));
                        imgv2.setImageBitmap((Bitmap) l.get(1));

                        if(/*canDetectVoice &&*/ canDetect && flag.get() && ((int) l.get(2)) > 0) {
                            new Thread(() -> {
                                try {
                                    Thread.sleep(2500);
                                    canDetect = true;
                                }
                                catch (Exception e) {

                                }
                            }).start();

                            canDetect = false;
                        }
                    }
                }
                catch (Exception e) {
                    Log.e("Processar itens reconhecidos", e.getMessage());
                }
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
            public void onError(int i) {
                speechRecognizer.destroy();
                speechRecognizer = null;
                initSpeechRecognizer();
            }

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
                else if(text.toLowerCase().startsWith("obrigado")) {
                    txt2Speech.speak("Imagina, estou aqui para ajudar", TextToSpeech.QUEUE_FLUSH, null, "");
                }
                else if(text.toLowerCase().startsWith("quem é você")) {
                    txt2Speech.speak("Eu sou a Make Me See, e ajudo pessoas com deficiência visual a se locomoverem de uma melhor forma", TextToSpeech.QUEUE_FLUSH, null, "");
                }
                else if(text.toLowerCase().startsWith("quem é seu pai")) {
                    txt2Speech.speak("Eu fui feita pelo Henrique Ricci", TextToSpeech.QUEUE_FLUSH, null, "");
                }
                else if(text.toLowerCase().startsWith("comente sobre o lucas veiga")) {
                    txt2Speech.speak("Ele é o primo querido do Henrique Ricci", TextToSpeech.QUEUE_FLUSH, null, "");
                }
                else if(text.toLowerCase().startsWith("reconhecer")) {
                    if(mCameraLeft == null && mCameraRight == null) {
                        txt2Speech.speak("Você ainda não conectou nenhuma camera", TextToSpeech.QUEUE_FLUSH, null, "");
                    }
                    else if(mCameraLeft == null) {
                        txt2Speech.speak("Ops, parece que voce ainda não conectou a camera esquerda", TextToSpeech.QUEUE_FLUSH, null, "");
                    }
                    else if(mCameraRight == null) {
                        txt2Speech.speak("Ops, parece que voce ainda não conectou a camera direira", TextToSpeech.QUEUE_FLUSH, null, "");
                    }
                    else {
                        txt2Speech.speak("Ok", TextToSpeech.QUEUE_FLUSH, null, "");
                        canDetectVoice = true;
                    }
                }
                else if(text.toLowerCase().startsWith("parar reconhecimento")) {
                    canDetectVoice = false;
                    txt2Speech.speak("Ok", TextToSpeech.QUEUE_FLUSH, null, "");
                }
                initSpeechRecognizer();
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
        sensorManager.registerListener(this, sensorGravity,
                SensorManager.SENSOR_DELAY_NORMAL);

        /*sensorManager = (SensorManager)getSystemService(SENSOR_SERVICE);
        sensorStep = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);*/
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

    protected float[] lowPassFilter( float[] input, float[] output ) {
        if ( output == null ) return input;
        for ( int i=0; i<input.length; i++ ) {
            output[i] = output[i] + 1.0f * (input[i] - output[i]);
        }
        return output;
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
            /*if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER){

                smoothed = lowPassFilter(event.values, gravity);
                gravity[0] = smoothed[0];
                gravity[1] = smoothed[1];
                gravity[2] = smoothed[2];

                if((Math.abs(prevY - gravity[1]) > 1) && !canDetect){
                    stepCount++;
                    canDetect = true;
                }
                prevY = gravity[1];
            }*/
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

    @Override
    public void onInit(int i) {
        if (i == TextToSpeech.SUCCESS) {
            //Setting speech Language
            int result = txt2Speech.setLanguage(Locale.getDefault());
            if(result==TextToSpeech.LANG_MISSING_DATA ||
                    result==TextToSpeech.LANG_NOT_SUPPORTED){
                Log.e("error", "This Language is not supported");
            }
            txt2Speech.setLanguage(Locale.getDefault());
            txt2Speech.setSpeechRate(2.9f);
            DecimalFormat decimalFormat = new DecimalFormat("#,#0.0");
            decimalFormat.setRoundingMode(RoundingMode.DOWN);
            txt2Speech.speak("Olá. Já estou pronta e podemos começar. Primeiro, conecte a camera esquerda", TextToSpeech.QUEUE_ADD, null, "1");
        }
        else {

        }
    }
}
