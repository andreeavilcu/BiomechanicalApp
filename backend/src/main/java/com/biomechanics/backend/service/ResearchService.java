package com.biomechanics.backend.service;

import com.biomechanics.backend.model.dto.research.AggregateMetricsDTO;
import com.biomechanics.backend.model.entity.BiomechanicsMetrics;
import com.biomechanics.backend.model.entity.User;
import com.biomechanics.backend.model.enums.RiskLevel;
import com.biomechanics.backend.model.dto.research.PostureTrendDTO;
import com.biomechanics.backend.repository.BiomechanicsMetricsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ResearchService {
    private final BiomechanicsMetricsRepository metricsRepository;

    public AggregateMetricsDTO getAggregateMetrics(LocalDate from, LocalDate to) {
        LocalDateTime start = from != null ? from.atStartOfDay() : LocalDateTime.now().minusYears(10);
        LocalDateTime end = to != null ? to.atTime(23, 59, 59) : LocalDateTime.now();

        return AggregateMetricsDTO.builder()
                .totalSessions(metricsRepository.countSessionsInPeriod(start, end))
                .averageGps(BigDecimal.valueOf(metricsRepository.getAverageGps(start, end)).setScale(2, RoundingMode.HALF_UP))
                .averageFhpAngle(BigDecimal.valueOf(metricsRepository.getAverageFhp(start, end)).setScale(2, RoundingMode.HALF_UP))
                .averageQAngle(BigDecimal.valueOf(metricsRepository.getAverageQAngle(start, end)).setScale(2, RoundingMode.HALF_UP))
                .build();
    }

    public List<PostureTrendDTO> getPostureTrends(int lastDays) {
        return metricsRepository.getPostureTrends(lastDays).stream()
                .map(row -> PostureTrendDTO.builder()
                        .date(row[0] instanceof java.sql.Date ? ((java.sql.Date) row[0]).toLocalDate() : (java.time.LocalDate) row[0])
                        .averageGps(BigDecimal.valueOf(((Number) row[1]).doubleValue()).setScale(2, RoundingMode.HALF_UP))
                        .sessionCount(((Number) row[2]).longValue())
                        .build())
                .collect(Collectors.toList());
    }

    public String exportAnonymizedCsv(LocalDate from, LocalDate to) {
        List<BiomechanicsMetrics> data = metricsRepository.findAll(); // În producție, adaugă filtrare pe date
        StringBuilder csv = new StringBuilder();
        csv.append("ageGroup,gender,qAngleLeft,qAngleRight,fhpAngle,shoulderAsymmetry,gps,riskLevel\n");

        for (BiomechanicsMetrics m : data) {
            User u = m.getScanSession().getUser();

            String ageGroup = getAgeGroup(u.getAge());
            csv.append(String.format("%s,%s,%s,%s,%s,%s,%s,%s\n",
                    ageGroup, u.getGender(), m.getQAngleLeft(), m.getQAngleRight(),
                    m.getFhpAngle(), m.getShoulderAsymmetryCm(), m.getGlobalPostureScore(), m.getRiskLevel()));
        }
        return csv.toString();
    }

    private String getAgeGroup(int age) {
        if (age < 18) return "UNDER_18";
        if (age < 30) return "18-29";
        if (age < 45) return "30-44";
        if (age < 60) return "45-59";
        return "60_PLUS";
    }
}
