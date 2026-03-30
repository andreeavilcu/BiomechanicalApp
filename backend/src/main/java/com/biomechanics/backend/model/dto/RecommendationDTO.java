package com.biomechanics.backend.model.dto;

import com.biomechanics.backend.model.enums.MetricType;
import com.biomechanics.backend.model.enums.RecommendationSeverity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecommendationDTO {
    private Long id;
    private MetricType metricType;
    private RecommendationSeverity severity;
    private String title;
    private String biomechanicalCause;
    private String exercise;
    private String ergonomicTip;
    private Boolean isBlocked;
    private Boolean disclaimerRequired;
    private String detectedValue;
    private String normalRange;
}