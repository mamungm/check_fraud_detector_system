package com.research.fraud.service;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImageResult {
    private String eventId;
    private String service;
    private ImageDetails details;
    private int latencyMs;
    private String status;
    private double ts;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class ImageDetails {
    private float image_duplicate_score;
    private boolean ocr_amount_match;
    private float layout_anomaly_score;
    private float font_anomaly_score;
    private float signature_presence_score;
    private float endorsement_score;
    private ArrayList<String> reasonCodes;
    private ImageExplanation explanation;
    private int latencyMs;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class ImageExplanation {
    private String ocrTextPreview;
    private ArrayList<Float> ocrAmountsDetected;
    private float expectedAmount;
    private Hashing hashing;
    private Heuristics heuristics;
    private ArrayList<String> limitations;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class Hashing {
    private String phash;
    private String dhash;
    private float duplicate_score;
    private String closest_event;
    private float closest_distance;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class Heuristics {
    private String layout;
    private String font;
    private String signature;
    private String endorsement;
}