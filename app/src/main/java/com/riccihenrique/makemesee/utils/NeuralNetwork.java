package com.riccihenrique.makemesee.utils;

import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.speech.tts.TextToSpeech;

import com.riccihenrique.makemesee.model.Obstacle;
import com.riccihenrique.makemesee.tflite.Classifier;
import com.riccihenrique.makemesee.tflite.TFLiteObjectDetectionAPIModel;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;

public class NeuralNetwork {

    private final float MINIMUM_CONFIDENCE = 0.5f;
    private static final boolean MAINTAIN_ASPECT = false;
    private static final int INPUT_SIZE = 300;
    private static final boolean IS_QUANTIZED = true;
    private static final String MODEL_FILE = "detect.tflite";
    private static final String LABELS_FILE = "file:///android_asset/labelmap.txt";
    private TextToSpeech textSpeech = null;

    private Classifier classifier;

    public NeuralNetwork(Context context) {
        textSpeech = new TextToSpeech(context, new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                textSpeech.setLanguage(Locale.getDefault());
            }
        });

        try {
            classifier = TFLiteObjectDetectionAPIModel.create(
                    context.getAssets(),
                    MODEL_FILE,
                    LABELS_FILE,
                    INPUT_SIZE,
                    IS_QUANTIZED);
        }
        catch (Exception e) {
            //showShortMsg("Ocorreu um erro ao ler o modelinho: " + e.getMessage());
        }
    }

    public List<Bitmap> recognize(Bitmap left, Bitmap right) {
        List<Bitmap> lb = new ArrayList<>();
        try {

            Bitmap croppedFrame = Bitmap.createScaledBitmap(left, INPUT_SIZE, INPUT_SIZE, false);
            final List<Classifier.Recognition> results = classifier.recognizeImage(croppedFrame);

            Canvas canvas = new Canvas(croppedFrame);
            final Paint paint = new Paint();
            paint.setColor(Color.RED);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(2.0f);

            Paint paintText = new Paint();
            paintText.setColor(Color.RED);
            paintText.setStrokeWidth(1.0f);

            List<Object> l = new ArrayList<>();
            final List<Obstacle> obstaclesRecognized = new LinkedList<Obstacle>();
            for (final Classifier.Recognition result : results) {
                final RectF location = result.getLocation();
                if (location != null && result.getConfidence() >= MINIMUM_CONFIDENCE) {
                    Obstacle obstacle = new Obstacle(result.getTitle(), result.getLocation(), result.getConfidence());
                    canvas.drawRect(location, paint);

                    l = Stereo.getObstavcleDistance(croppedFrame, Bitmap.createScaledBitmap(right, INPUT_SIZE, INPUT_SIZE, false), location);
                    obstacle.setDistance((float) l.get(1));
                    canvas.drawText(obstacle.toString(), location.left, location.top - 5, paintText);
                    result.setLocation(location);

                    textSpeech.speak(obstacle.toString(),TextToSpeech.QUEUE_ADD,null);

                    obstaclesRecognized.add(obstacle);
                }
            }
            lb.add(croppedFrame);
            lb.add((Bitmap) (l.size() > 0 ? l.get(0) : croppedFrame));

            return lb;
        }
        catch (Exception e) {
           // showShortMsg("Krai, não deu" + e.getMessage());

        }
        return lb;
    }
}