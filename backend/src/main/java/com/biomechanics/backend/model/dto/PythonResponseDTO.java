package com.biomechanics.backend.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;
@Data
@NoArgsConstructor
public class PythonResponseDTO {
    private KeypointDTO nose;

    @JsonProperty("l_ear")
    private KeypointDTO lEar;

    @JsonProperty("r_ear")
    private KeypointDTO rEar;

    private KeypointDTO neck;

    @JsonProperty("l_shoulder")
    private KeypointDTO lShoulder;

    @JsonProperty("r_shoulder")
    private KeypointDTO rShoulder;

    @JsonProperty("l_hip")
    private KeypointDTO lHip;

    @JsonProperty("r_hip")
    private KeypointDTO rHip;

    private KeypointDTO pelvis;

    @JsonProperty("l_knee")
    private KeypointDTO lKnee;

    @JsonProperty("r_knee")
    private KeypointDTO rKnee;

    @JsonProperty("l_ankle")
    private KeypointDTO lAnkle;

    @JsonProperty("r_ankle")
    private KeypointDTO rAnkle;

    private MetadataDTO meta;

    @Data
    @NoArgsConstructor
    public static class KeypointDTO {
        private Double x;
        private Double y;
        private Double z;
    }

    @Data
    @NoArgsConstructor
    public static class MetadataDTO {
        private String method;

        @JsonProperty("target_height")
        private Double targetHeight;

        @JsonProperty("scaling_factor")
        private Double scalingFactor;

        @JsonProperty("best_score")
        private Double bestScore;
    }
}
