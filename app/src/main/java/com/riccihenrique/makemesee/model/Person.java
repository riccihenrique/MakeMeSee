package com.riccihenrique.makemesee.model;

import android.graphics.Bitmap;
import android.util.Log;

import com.riccihenrique.makemesee.dlib.FaceDet;
import com.riccihenrique.makemesee.dlib.VisionDetRet;

import java.util.List;

public class Person {
    private FaceDet mFaceDet;

    public Person() {
        mFaceDet = new FaceDet();
    }
    public List<VisionDetRet> detectPeople(Bitmap bmp) {
        long startTime = System.currentTimeMillis();
        List<VisionDetRet> results;
        results = mFaceDet.detect(bmp);
        long endTime = System.currentTimeMillis();
        Log.d("TESTE", (startTime - endTime) + "");
        return results;
    }
}
