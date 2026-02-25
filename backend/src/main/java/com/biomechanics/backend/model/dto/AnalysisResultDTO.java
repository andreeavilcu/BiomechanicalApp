package com.biomechanics.backend.model.dto;
import com.biomechanics.backend.model.enums.ProcessingStatus;
import com.biomechanics.backend.model.enums.RiskLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;


@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalysisResultDTO {
    private Long sessionId;
    private LocalDateTime scanDate;
    private ProcessingStatus status;
    private String errorMessage;

    private String processingMethod;
    private BigDecimal aiConfidenceScore;
    private BigDecimal scalingFactor;

    private BigDecimal qAngleLeft;
    private BigDecimal qAngleRight;
    private BigDecimal fhpAngle;
    private BigDecimal fhpDistanceCm;
    private BigDecimal shoulderAsymmetryCm;

    private BigDecimal stancePhaseLeft;
    private BigDecimal stancePhaseRight;
    private BigDecimal cadence;

    private BigDecimal globalPostureScore;
    private RiskLevel riskLevel;

    private List<String> recommendations;

    private EvolutionDTO evolution;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EvolutionDTO {
        private BigDecimal postureScoreChange;
        private String trend;
        private Integer daysSinceLastScan;
    }
}
