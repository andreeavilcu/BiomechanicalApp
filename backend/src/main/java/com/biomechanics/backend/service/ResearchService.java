package com.biomechanics.backend.service;

import com.biomechanics.backend.model.dto.research.AggregateMetricsDTO;
import com.biomechanics.backend.model.dto.research.PostureTrendDTO;
import com.biomechanics.backend.model.entity.BiomechanicsMetrics;
import com.biomechanics.backend.model.entity.User;
import com.biomechanics.backend.repository.BiomechanicsMetricsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ResearchService {
    private final BiomechanicsMetricsRepository metricsRepository;

    public AggregateMetricsDTO getAggregateMetrics(LocalDate from, LocalDate to) {
        LocalDateTime start = from != null ? from.atStartOfDay() : LocalDateTime.now().minusYears(10);
        LocalDateTime end = to != null ? to.atTime(23, 59, 59) : LocalDateTime.now();

        Long totalSessions = metricsRepository.countSessionsInPeriod(start, end);

        Double avgGps = metricsRepository.getAverageGps(start, end);
        Double avgFhp = metricsRepository.getAverageFhp(start, end);
        Double avgQAngle = metricsRepository.getAverageQAngle(start, end);
        Double stdDevGps = metricsRepository.getStdDevGps(start, end);
        Double p25Gps = metricsRepository.getPercentileGps(start, end, 0.25);
        Double p75Gps = metricsRepository.getPercentileGps(start, end, 0.75);

        return AggregateMetricsDTO.builder()
                .totalSessions(totalSessions != null ? totalSessions : 0L)
                .averageGps(toBigDecimal(avgGps))
                .averageFhpAngle(toBigDecimal(avgFhp))
                .averageQAngle(toBigDecimal(avgQAngle))
                .stdDevGps(toBigDecimal(stdDevGps))
                .p25Gps(toBigDecimal(p25Gps))
                .p75Gps(toBigDecimal(p75Gps))
                .build();
    }

    public List<PostureTrendDTO> getPostureTrends(int lastDays) {
        List<Object[]> results = metricsRepository.getPostureTrends(lastDays);

        return results.stream()
                .map(row -> PostureTrendDTO.builder()
                        .date(row[0] instanceof java.sql.Date
                                ? ((java.sql.Date) row[0]).toLocalDate()
                                : (LocalDate) row[0])
                        .averageGps(toBigDecimal(row[1] != null ? ((Number) row[1]).doubleValue() : null))
                        .sessionCount(row[2] != null ? ((Number) row[2]).longValue() : 0L)
                        .build())
                .collect(Collectors.toList());
    }

    public String exportAnonymizedCsv(LocalDate from, LocalDate to) {
        List<BiomechanicsMetrics> data = metricsRepository.findAll();

        StringBuilder csv = new StringBuilder();
        csv.append("ageGroup,gender,qAngleLeft,qAngleRight,fhpAngle,shoulderAsymmetry,gps,riskLevel\n");

        for (BiomechanicsMetrics m : data) {
            if (m.getScanSession() == null || m.getScanSession().getUser() == null) {
                continue;
            }

            User u = m.getScanSession().getUser();
            String ageGroup = getAgeGroup(u.getAge());

            csv.append(String.format("%s,%s,%s,%s,%s,%s,%s,%s\n",
                    ageGroup,
                    u.getGender() != null ? u.getGender() : "UNKNOWN",
                    formatValue(m.getQAngleLeft()),
                    formatValue(m.getQAngleRight()),
                    formatValue(m.getFhpAngle()),
                    formatValue(m.getShoulderAsymmetryCm()),
                    formatValue(m.getGlobalPostureScore()),
                    m.getRiskLevel() != null ? m.getRiskLevel() : "UNKNOWN"));
        }
        return csv.toString();
    }

    private BigDecimal toBigDecimal(Double value) {
        if (value == null) {
            return null; // Corectat aici
        }
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }

    private String formatValue(BigDecimal value) {
        return value != null ? value.toString() : "";
    }

    private String getAgeGroup(int age) {
        if (age < 18) return "UNDER_18";
        if (age < 30) return "18-29";
        if (age < 45) return "30-44";
        if (age < 60) return "45-59";
        return "60_PLUS";
    }
}
