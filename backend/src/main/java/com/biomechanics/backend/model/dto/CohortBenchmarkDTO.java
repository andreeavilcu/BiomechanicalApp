package com.biomechanics.backend.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CohortBenchmarkDTO {

    private MetricStats gps;
    private MetricStats fhpAngle;
    private MetricStats qAngle;
    private MetricStats shoulderAsymmetry;

    private Long totalSessions;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MetricStats {
        private Double p25;
        private Double p50;
        private Double p75;
        private Double avg;
    }
}