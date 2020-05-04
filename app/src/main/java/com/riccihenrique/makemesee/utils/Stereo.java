package com.riccihenrique.makemesee.utils;

import android.graphics.Bitmap;
import android.graphics.RectF;

import com.riccihenrique.makemesee.model.Obstacle;

import org.opencv.android.Utils;
import org.opencv.calib3d.StereoSGBM;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.List;

public class Stereo {

    public static List<Object> getObstavcleDistance(Bitmap bmpleft, Bitmap bmpright, RectF position) {
        Mat left = new Mat();
        Mat right = new Mat();

        Utils.bitmapToMat(bmpleft, left);
        Utils.bitmapToMat(bmpright, right);

        Mat disparity = getDisparityMap(left, right);
        Bitmap bmpDisparity = Bitmap.createBitmap(bmpleft);
        Utils.matToBitmap(disparity, bmpDisparity);
        int a = bmpDisparity.getPixel( (int) position.left + 10, (int) position.top + 10);
        List<Object> l = new ArrayList<>();
        l.add(bmpDisparity);
        l.add(1.40f);
        return l;
    }

    public static Mat getDisparityMap(Mat imgleft, Mat imgright) {
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
        Imgproc.applyColorMap(disparity, disparity, Imgproc.COLORMAP_JET);

        return disparity;
    }
}
