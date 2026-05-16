package com.biomechanics.backend.service;

import com.biomechanics.backend.model.dto.CohortBenchmarkDTO;
import com.biomechanics.backend.repository.BiomechanicsMetricsRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CohortBenchmarkService Tests")
class CohortBenchmarkServiceTest {

    @Mock
    private BiomechanicsMetricsRepository metricsRepository;

    @InjectMocks
    private CohortBenchmarkService cohortBenchmarkService;

    @Nested
    @DisplayName("getBenchmark()")
    class GetBenchmark {

        private List<Object[]> validRow(double p25, double p50, double p75, double avg) {
            List<Object[]> rows = new ArrayList<>();
            rows.add(new Object[]{p25, p50, p75, avg});
            return rows;
        }

        @Test
        @DisplayName("Valid data - returns DTO with populated statistics")
        void shouldReturnPopulatedBenchmark() {
            when(metricsRepository.countAllMetrics()).thenReturn(100L);
            when(metricsRepository.getGpsBenchmark())
                    .thenReturn(validRow(20.0, 30.0, 45.0, 32.0));
            when(metricsRepository.getFhpBenchmark())
                    .thenReturn(validRow(5.0, 10.0, 15.0, 11.0));
            when(metricsRepository.getQAngleBenchmark())
                    .thenReturn(validRow(10.0, 13.0, 16.0, 13.5));
            when(metricsRepository.getShoulderAsymmetryBenchmark())
                    .thenReturn(validRow(0.5, 1.0, 2.0, 1.2));

            CohortBenchmarkDTO result = cohortBenchmarkService.getBenchmark();

            assertThat(result.getTotalSessions()).isEqualTo(100L);
            assertThat(result.getGps().getP50()).isEqualTo(30.0);
            assertThat(result.getGps().getAvg()).isEqualTo(32.0);
            assertThat(result.getFhpAngle().getP25()).isEqualTo(5.0);
            assertThat(result.getQAngle().getP75()).isEqualTo(16.0);
            assertThat(result.getShoulderAsymmetry().getAvg()).isEqualTo(1.2);
        }

        @Test
        @DisplayName("Query with no data - returns empty MetricStats (no exception)")
        void shouldReturnEmptyStatsWhenNoData() {
            when(metricsRepository.countAllMetrics()).thenReturn(0L);
            when(metricsRepository.getGpsBenchmark()).thenReturn(Collections.emptyList());
            when(metricsRepository.getFhpBenchmark()).thenReturn(Collections.emptyList());
            when(metricsRepository.getQAngleBenchmark()).thenReturn(Collections.emptyList());
            when(metricsRepository.getShoulderAsymmetryBenchmark()).thenReturn(Collections.emptyList());

            CohortBenchmarkDTO result = cohortBenchmarkService.getBenchmark();

            assertThat(result.getTotalSessions()).isEqualTo(0L);
            assertThat(result.getGps().getP50()).isNull();
            assertThat(result.getFhpAngle().getAvg()).isNull();
        }

        @Test
        @DisplayName("Row with null values - corresponding fields are null")
        void shouldHandleNullValuesInRow() {
            List<Object[]> nullRow = new ArrayList<>();
            nullRow.add(new Object[]{null, null, null, null});
            when(metricsRepository.countAllMetrics()).thenReturn(5L);
            when(metricsRepository.getGpsBenchmark()).thenReturn(nullRow);
            when(metricsRepository.getFhpBenchmark()).thenReturn(Collections.emptyList());
            when(metricsRepository.getQAngleBenchmark()).thenReturn(Collections.emptyList());
            when(metricsRepository.getShoulderAsymmetryBenchmark()).thenReturn(Collections.emptyList());

            CohortBenchmarkDTO result = cohortBenchmarkService.getBenchmark();

            assertThat(result.getGps().getP25()).isNull();
            assertThat(result.getGps().getP50()).isNull();
            assertThat(result.getGps().getAvg()).isNull();
        }

        @Test
        @DisplayName("Short row (less than 4 columns) - returns empty MetricStats")
        void shouldHandleMalformedRow() {
            List<Object[]> shortRow = new ArrayList<>();
            shortRow.add(new Object[]{20.0, 30.0});
            when(metricsRepository.countAllMetrics()).thenReturn(1L);
            when(metricsRepository.getGpsBenchmark()).thenReturn(shortRow);
            when(metricsRepository.getFhpBenchmark()).thenReturn(Collections.emptyList());
            when(metricsRepository.getQAngleBenchmark()).thenReturn(Collections.emptyList());
            when(metricsRepository.getShoulderAsymmetryBenchmark()).thenReturn(Collections.emptyList());

            assertThatNoException().isThrownBy(() -> cohortBenchmarkService.getBenchmark());
        }

        @Test
        @DisplayName("totalSessions is retrieved from repository")
        void shouldDelegateCountToRepository() {
            when(metricsRepository.countAllMetrics()).thenReturn(42L);
            when(metricsRepository.getGpsBenchmark()).thenReturn(Collections.emptyList());
            when(metricsRepository.getFhpBenchmark()).thenReturn(Collections.emptyList());
            when(metricsRepository.getQAngleBenchmark()).thenReturn(Collections.emptyList());
            when(metricsRepository.getShoulderAsymmetryBenchmark()).thenReturn(Collections.emptyList());

            CohortBenchmarkDTO result = cohortBenchmarkService.getBenchmark();

            assertThat(result.getTotalSessions()).isEqualTo(42L);
            verify(metricsRepository).countAllMetrics();
        }
    }
}
