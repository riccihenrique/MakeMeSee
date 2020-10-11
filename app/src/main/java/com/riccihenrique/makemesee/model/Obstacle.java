package com.riccihenrique.makemesee.model;

import android.graphics.RectF;

import java.math.RoundingMode;
import java.text.DecimalFormat;

public class Obstacle {
    private String description;
    private RectF location;
    private double distance;
    private String name;
    private float confidence;

    public Obstacle(String description, RectF location, float confidence) {
        this.description = description;
        this.location = location;
        this.confidence = confidence;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public RectF getLocation() {
        return location;
    }

    public void setLocation(RectF location) {
        this.location = location;
    }

    public double getDistance() {
        return distance;
    }

    public void setDistance(double distance) {
        this.distance = distance;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public float getConfidence() {
        return confidence;
    }

    public void setConfidence(float confidence) {
        this.confidence = confidence;
    }

    @Override
    public String toString() {
        DecimalFormat decimalFormat = new DecimalFormat("#,#0.0");
        decimalFormat.setRoundingMode(RoundingMode.DOWN);
        return description.equals("pessoa") ? name + " está se aproximando" : description + " a " + decimalFormat.format(distance) + " metros";
    }
}
