package com.biomechanics.backend.service;

import com.biomechanics.backend.model.dto.research.AggregateMetricsDTO;
import com.biomechanics.backend.model.enums.RiskLevel;
import com.biomechanics.backend.model.dto.research.PostureTrendDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ResearchService {
    public AggregateMetricsDTO getAggregateMetrics(LocalDate from, LocalDate to) {
        // TODO: implementare cu query-uri JPA agregate pe BiomechanicsMetrics
        // Nu se expun userId, email sau alte PII
        return AggregateMetricsDTO.builder()
                .totalSessions(0L)
                .averageGps(BigDecimal.ZERO)
                .averageFhpAngle(BigDecimal.ZERO)
                .averageQAngle(BigDecimal.ZERO)
                .build();
    }

    public Map<String, Map<RiskLevel, Long>> getRiskDistributionByDemographics() {
        // TODO: grupare pe age_group (18-30, 31-45, 46-60, 60+) și gen
        return Map.of();
    }

    public List<PostureTrendDTO> getPostureTrends(int lastDays) {
        // TODO: GPS mediu pe zi pentru ultimele N zile
        return List.of();
    }

    public String exportAnonymizedCsv(LocalDate from, LocalDate to) {
        // TODO: generare CSV cu coloane: ageGroup, gender, qAngleLeft, qAngleRight,
        //       fhpAngle, shoulderAsymmetry, gps, riskLevel
        // NICIODATĂ: userId, email, firstName, lastName, dateOfBirth
        StringBuilder csv = new StringBuilder();
        csv.append("ageGroup,gender,qAngleLeft,qAngleRight,fhpAngle,shoulderAsymmetry,gps,riskLevel\n");
        return csv.toString();
    }
}
