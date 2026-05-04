package com.biomechanics.backend.service;

import com.biomechanics.backend.model.dto.CohortBenchmarkDTO;
import com.biomechanics.backend.repository.BiomechanicsMetricsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CohortBenchmarkService {

    private final BiomechanicsMetricsRepository metricsRepository;

    public CohortBenchmarkDTO getBenchmark() {
        Long total = metricsRepository.countAllMetrics();

        log.info("Calculating cohort benchmark across {} sessions", total);

        return CohortBenchmarkDTO.builder()
                .totalSessions(total)
                .gps(toMetricStats(metricsRepository.getGpsBenchmark(), "GPS"))
                .fhpAngle(toMetricStats(metricsRepository.getFhpBenchmark(), "FHP"))
                .qAngle(toMetricStats(metricsRepository.getQAngleBenchmark(), "QAngle"))
                .shoulderAsymmetry(toMetricStats(metricsRepository.getShoulderAsymmetryBenchmark(), "Shoulder"))
                .build();
    }

    private CohortBenchmarkDTO.MetricStats toMetricStats(List<Object[]> rows, String label) {
        if (rows == null || rows.isEmpty()) {
            log.warn("Benchmark query for {} returned no rows", label);
            return CohortBenchmarkDTO.MetricStats.builder().build();
        }

        Object[] row = rows.get(0);
        if (row == null || row.length < 4) {
            log.warn("Benchmark query for {} returned malformed row (length={})",
                    label, row == null ? 0 : row.length);
            return CohortBenchmarkDTO.MetricStats.builder().build();
        }

        log.debug("Benchmark {} raw row: p25={} ({}), p50={} ({}), p75={} ({}), avg={} ({})",
                label,
                row[0], row[0] == null ? "null" : row[0].getClass().getSimpleName(),
                row[1], row[1] == null ? "null" : row[1].getClass().getSimpleName(),
                row[2], row[2] == null ? "null" : row[2].getClass().getSimpleName(),
                row[3], row[3] == null ? "null" : row[3].getClass().getSimpleName());

        return CohortBenchmarkDTO.MetricStats.builder()
                .p25(toDouble(row[0]))
                .p50(toDouble(row[1]))
                .p75(toDouble(row[2]))
                .avg(toDouble(row[3]))
                .build();
    }

    private Double toDouble(Object o) {
        if (o == null) return null;
        if (o instanceof Number n) return n.doubleValue();
        return null;
    }
}