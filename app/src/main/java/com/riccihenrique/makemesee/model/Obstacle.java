package com.riccihenrique.makemesee.model;

import android.graphics.RectF;

public class Obstacle {
    private String description;
    private RectF location;
    private float distance;
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

    public float getDistance() {
        return distance;
    }

    public void setDistance(float distance) {
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
        return description.equals("person") ? name + " está se aproximando" : description + " a " + distance + " metros";
    }
}
