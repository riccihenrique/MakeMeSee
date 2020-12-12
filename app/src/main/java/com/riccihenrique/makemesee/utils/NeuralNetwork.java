package com.riccihenrique.makemesee.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.speech.tts.TextToSpeech;
import android.util.Log;

import com.riccihenrique.makemesee.dlib.VisionDetRet;
import com.riccihenrique.makemesee.model.Obstacle;
import com.riccihenrique.makemesee.model.Person;

import java.util.ArrayList;
import java.util.List;

public class NeuralNetwork {

    private final float MINIMUM_CONFIDENCE = 0.7f;
    private static final int INPUT_SIZE = 300;
    private static final boolean IS_QUANTIZED = true;
    private static final String MODEL_FILE = "detect.tflite";
    private static final String LABELS_FILE = "file:///android_asset/labelmap.txt";
    private TextToSpeech textSpeech = null;
    private Stereo stereo = null;
    private Classifier classifier = null;
    private Paint paintText = null;
    private Paint paint = null;

    private Person p;
    public NeuralNetwork(Context context, TextToSpeech txt2Speech) {
        textSpeech = txt2Speech;
        stereo = new Stereo();
        paint = new Paint();
        paint.setColor(Color.RED);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2.0f);

        paintText = new Paint();
        paintText.setColor(Color.RED);
        paintText.setStrokeWidth(1.0f);
        p = new Person();
        try {
            classifier = ObjectDetectorModel.create(
                    context.getAssets(),
                    MODEL_FILE,
                    LABELS_FILE,
                    INPUT_SIZE,
                    IS_QUANTIZED);
        }
        catch (Exception e) {
            Log.e("LOAD MODEL/PERSON", e.getMessage());
        }
    }

    public List<Object> recognize(Bitmap left, Bitmap right, boolean canDetect) {
        List<Object> lb = new ArrayList<>();
        try {
            int c = 0;
            Bitmap croppedFrame = Bitmap.createScaledBitmap(left, INPUT_SIZE, INPUT_SIZE, false);
            Bitmap disparityMap = stereo.getDisparityMap(croppedFrame, Bitmap.createScaledBitmap(right, INPUT_SIZE, INPUT_SIZE, false));

            if(canDetect) {
                long startTime = System.currentTimeMillis();
                List<Classifier.Recognition> results = classifier.recognizeImage(croppedFrame);
                long endTime = System.currentTimeMillis();
                Log.d("RECONHECIMENTO OBSTACULOS", (endTime - startTime) + "");

                Canvas canvas = new Canvas(croppedFrame);
                List<Obstacle> obstaclesRecognized = new ArrayList<>();

                for (Classifier.Recognition result : results) {
                    RectF location = result.getLocation();
                    if (location != null && result.getConfidence() >= MINIMUM_CONFIDENCE) {
                        Obstacle obstacle = new Obstacle(result.getTitle(), result.getLocation(), result.getConfidence());
                        obstacle.setDistance(stereo.getDistance(disparityMap, location));

                        if(obstacle.getDescription().toLowerCase().equals("pessoa")) {
                            int x, y, w, h;
                            x = (int) location.left;
                            y = (int) location.top;
                            w = (int) (location.right - location.left);
                            h = (int) (location.bottom - location.top);

                            x = x > croppedFrame.getWidth() ? croppedFrame.getWidth() : (x < 0 ? 0 : x);
                            y = y > croppedFrame.getHeight() ? croppedFrame.getHeight() : (y < 0 ? 0 : y);
                            w = w > croppedFrame.getWidth() ? croppedFrame.getWidth() : (w < 0 ? 0 : w);
                            h = h > croppedFrame.getHeight() ? croppedFrame.getHeight() : (h < 0 ? 0 : h);

                            try {
                                List<VisionDetRet> personRecognized = p.detectPeople(Bitmap.createBitmap(croppedFrame, x, y, w, h));
                                obstacle.setName(personRecognized.get(0).getName().toString());
                            }
                            catch (Exception ex) {
                                obstacle.setName("");
                            }
                        }

                        canvas.drawRect(location, paint);
                        canvas.drawText(obstacle.toString(), location.left, location.top - 5, paintText);
                        result.setLocation(location);

                        if(obstacle.getDistance() <= 7)
                            obstaclesRecognized.add(obstacle);
                    }
                }

                obstaclesRecognized.sort((obstacle, t1) -> (int) (obstacle.getDistance() - t1.getDistance()));
                for(Obstacle obs : obstaclesRecognized) {
                    c++;
                    textSpeech.speak(obs.toString(), TextToSpeech.QUEUE_ADD,null, "");
                }
            }

            lb.add(croppedFrame);
            lb.add(disparityMap);
            lb.add(c);

            return lb;
        }
        catch (Exception e) {
            Log.e("NeuralNetwork", e.getMessage() + " ---- " + e.getStackTrace().toString());
        }
        return lb;
    }
}