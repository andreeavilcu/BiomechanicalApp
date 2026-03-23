package com.biomechanics.backend.model.dto.research;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;


@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AggregateMetricsDTO {
    private Long totalSessions;
    private BigDecimal averageGps;
    private BigDecimal averageFhpAngle;
    private BigDecimal averageQAngle;
    private BigDecimal stdDevGps;
    private BigDecimal p25Gps;
    private BigDecimal p75Gps;
}
