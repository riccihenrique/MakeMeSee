package com.riccihenrique.makemesee.utils;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;

import org.opencv.android.Utils;
import org.opencv.calib3d.StereoSGBM;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.List;

public class Stereo {
    private static double distance;
    public static List<Object> getObstacleDistance(Bitmap bmpleft, Bitmap bmpright, RectF position) {
        Mat left = new Mat();
        Mat right = new Mat();

        Utils.bitmapToMat(bmpleft, left);
        Utils.bitmapToMat(bmpright, right);

        Mat disparity = getDisparityMap(left, right);
        Bitmap bmpDisparity = Bitmap.createBitmap(bmpleft);
        Utils.matToBitmap(disparity, bmpDisparity);
        bmpDisparity = getDistance(bmpDisparity, position);
        List<Object> l = new ArrayList<>();
        l.add(bmpDisparity);
        l.add(distance);
        return l;
    }

    private static Mat getDisparityMap(Mat imgleft, Mat imgright) {
        Mat left = new Mat();
        Mat right = new Mat();

        Imgproc.cvtColor(imgleft, left, Imgproc.COLOR_BGR2GRAY);
        Imgproc.cvtColor(imgright, right, Imgproc.COLOR_BGR2GRAY);

        // Create a new image using the size and type of the left image
        Mat disparity = new Mat(left.size(), left.type());

        int numDisparity = (int)(left.size().width/8);

        StereoSGBM stereo = StereoSGBM.create(
                0,    // min DIsparities
                numDisparity, // numDisparities
                13,   // SADWindowSize
                2*11*11,   // 8*number_of_image_channels*SADWindowSize*SADWindowSize   // p1
                5*11*11,  // 8*number_of_image_channels*SADWindowSize*SADWindowSize  // p2
                -1,   // disp12MaxDiff
                63,   // prefilterCap
                10,   // uniqueness ratio
                0, // sreckleWindowSize
                32, // spreckle Range
                0); // full DP
        // create the DisparityMap - SLOW: O(Width*height*numDisparity)
        stereo.compute(left, right, disparity);
        Core.normalize(disparity, disparity, 0, 255, Core.NORM_MINMAX);
        disparity.convertTo(disparity, CvType.CV_8UC1);
        return disparity;
    }

    private static Bitmap getDistance(Bitmap disparit, RectF rect) {
        int [] colors = new int[256];
        float x, y, width, height;
        x = rect.left < 0 ? 0 : rect.left;
        y = rect.top < 0 ? 0 : rect.top;
        width = rect.right > disparit.getWidth() ? disparit.getWidth() : rect.right;
        height = rect.bottom > disparit.getHeight() ? disparit.getHeight() : rect.bottom;

        for(; y < height; y++)
            for(; x < width; x++) {
                colors[Color.red(disparit.getPixel((int) x, (int) y))]++;
            }
        int s = 0, c = 0;
        int i = 50;
        while(i < 256) {
            if(colors[i] > 1) {
                s += i;
                c++;
            }
            i++;
        }

        Canvas canvas = new Canvas(disparit);
        Paint paint = new Paint();
        paint.setColor(Color.RED);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.0f);

        canvas.drawRect(rect, paint);
        double result = s / c;
        distance = 0.000716042 * Math.pow(result, 2) - 0.236778 * result + 21.4951;

        return disparit;
    }
}
