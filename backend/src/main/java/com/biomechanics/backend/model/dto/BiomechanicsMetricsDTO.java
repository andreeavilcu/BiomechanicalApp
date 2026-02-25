package com.biomechanics.backend.model.dto;

import com.biomechanics.backend.model.enums.RiskLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;


@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BiomechanicsMetricsDTO {
    private Long sessionId;
    private LocalDateTime scanDate;

    private BigDecimal qAngleLeft;
    private BigDecimal qAngleRight;
    private BigDecimal fhpAngle;
    private BigDecimal shoulderAsymmetryCm;

    private BigDecimal globalPostureScore;
    private RiskLevel riskLevel;
}
