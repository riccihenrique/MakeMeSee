package com.riccihenrique.makemesee.utils;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.RectF;

import org.opencv.android.Utils;
import org.opencv.calib3d.StereoSGBM;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;
import java.util.Arrays;

public class Stereo {
    public Bitmap getDisparityMap(Bitmap bmpleft, Bitmap bmpright) {
        Mat left = new Mat();
        Mat right = new Mat();
        Utils.bitmapToMat(bmpleft, left);
        Utils.bitmapToMat(bmpright, right);

        Mat disparity = getDisparityMap(left, right);
        Bitmap bmpDisparity = Bitmap.createBitmap(bmpleft);
        Utils.matToBitmap(disparity, bmpDisparity);
        return bmpDisparity;
    }

    private Mat getDisparityMap(Mat imgleft, Mat imgright) {
        Mat left = new Mat();
        Mat right = new Mat();

        Imgproc.cvtColor(imgleft, left, Imgproc.COLOR_BGR2GRAY);
        Imgproc.cvtColor(imgright, right, Imgproc.COLOR_BGR2GRAY);

        Mat disparity = new Mat(left.size(), left.type());

        StereoSGBM stereo = StereoSGBM.create(
                0,    // min DIsparities
                32, // numDisparities
                9,   // SADWindowSize
                2*11*11,   // 8*number_of_image_channels*SADWindowSize*SADWindowSize   // p1
                5*11*11,  // 8*number_of_image_channels*SADWindowSize*SADWindowSize  // p2
                -1,   // disp12MaxDiff
                63,   // prefilterCap
                10,   // uniqueness ratio
                0, // sreckleWindowSize
                32, // spreckle Range
                0); // full DP

        stereo.compute(left, right, disparity);
        Core.normalize(disparity, disparity, 0, 255, Core.NORM_MINMAX);
        disparity.convertTo(disparity, CvType.CV_8UC1);
        return disparity;
    }

    public double getDistance(Bitmap disparit, RectF rect) {
        //disparit = this.disp;
        int [] colors = new int[256];
        float x, y, width, height;
        x = rect.left < 0 ? 0 : rect.left;
        y = rect.top < 0 ? 0 : rect.top;
        width = rect.right > disparit.getWidth() ? disparit.getWidth() : rect.right;
        height = rect.bottom > disparit.getHeight() ? disparit.getHeight() : rect.bottom;

        for(; y < height; y++)
            for(; x < width; x++) {
                if(Color.red(disparit.getPixel((int) x, (int) y)) > 10 && Color.red(disparit.getPixel((int) x, (int) y))  < 254) {
                    colors[Color.red(disparit.getPixel((int) x, (int) y))] = colors[Color.red(disparit.getPixel((int) x, (int) y))] + 1;
                }
            }
        int s = 0, c = 0, i = 253;
        Arrays.sort(colors);
        while(i > 10 && colors[i] > 0) {
            s += i;
            c++;
            i--;
        }

        if(c == 0)
            return 0;

        double result = s / c;
        return  0.000716042 * Math.pow(result, 2) - 0.236778 * result + 21.4951;
    }
}
