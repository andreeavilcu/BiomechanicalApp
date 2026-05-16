package com.biomechanics.backend.service;

import com.biomechanics.backend.model.dto.research.AggregateMetricsDTO;
import com.biomechanics.backend.model.dto.research.PostureTrendDTO;
import com.biomechanics.backend.model.entity.BiomechanicsMetrics;
import com.biomechanics.backend.model.entity.ScanSession;
import com.biomechanics.backend.model.entity.User;
import com.biomechanics.backend.model.enums.Gender;
import com.biomechanics.backend.model.enums.RiskLevel;
import com.biomechanics.backend.model.enums.UserRole;
import com.biomechanics.backend.repository.BiomechanicsMetricsRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ResearchService Tests")
class ResearchServiceTest {

    @Mock private BiomechanicsMetricsRepository metricsRepository;

    @InjectMocks
    private ResearchService researchService;

    @Nested
    @DisplayName("getAggregateMetrics()")
    class GetAggregateMetrics {

        @Test
        @DisplayName("Aggregate metrics are calculated and returned correctly")
        void shouldReturnAggregateMetrics() {
            when(metricsRepository.countSessionsInPeriod(any(LocalDateTime.class), any(LocalDateTime.class)))
                    .thenReturn(42L);
            when(metricsRepository.getAverageGps(any(), any())).thenReturn(72.5);
            when(metricsRepository.getAverageFhp(any(), any())).thenReturn(8.3);
            when(metricsRepository.getAverageQAngle(any(), any())).thenReturn(16.0);
            when(metricsRepository.getStdDevGps(any(), any())).thenReturn(5.2);
            when(metricsRepository.getPercentileGps(any(), any(), eq(0.25))).thenReturn(65.0);
            when(metricsRepository.getPercentileGps(any(), any(), eq(0.75))).thenReturn(80.0);

            AggregateMetricsDTO result = researchService.getAggregateMetrics(
                    LocalDate.of(2024, 1, 1), LocalDate.of(2024, 12, 31));

            assertThat(result.getTotalSessions()).isEqualTo(42L);
            assertThat(result.getAverageGps()).isNotNull();
            assertThat(result.getAverageFhpAngle()).isNotNull();
            assertThat(result.getAverageQAngle()).isNotNull();
            assertThat(result.getP25Gps()).isNotNull();
            assertThat(result.getP75Gps()).isNotNull();
        }

        @Test
        @DisplayName("No data (null from DB) - totalSessions becomes 0, fields remain null")
        void shouldHandleNullValuesFromDb() {
            when(metricsRepository.countSessionsInPeriod(any(), any())).thenReturn(null);
            when(metricsRepository.getAverageGps(any(), any())).thenReturn(null);
            when(metricsRepository.getAverageFhp(any(), any())).thenReturn(null);
            when(metricsRepository.getAverageQAngle(any(), any())).thenReturn(null);
            when(metricsRepository.getStdDevGps(any(), any())).thenReturn(null);
            when(metricsRepository.getPercentileGps(any(), any(), anyDouble())).thenReturn(null);

            AggregateMetricsDTO result = researchService.getAggregateMetrics(null, null);

            assertThat(result.getTotalSessions()).isZero();
            assertThat(result.getAverageGps()).isNull();
        }

        @Test
        @DisplayName("No date range (from=null, to=null) - repository is called with default values")
        void shouldUseDefaultDateRangeWhenNullProvided() {
            when(metricsRepository.countSessionsInPeriod(any(), any())).thenReturn(10L);
            when(metricsRepository.getAverageGps(any(), any())).thenReturn(70.0);
            when(metricsRepository.getAverageFhp(any(), any())).thenReturn(7.0);
            when(metricsRepository.getAverageQAngle(any(), any())).thenReturn(14.0);
            when(metricsRepository.getStdDevGps(any(), any())).thenReturn(3.0);
            when(metricsRepository.getPercentileGps(any(), any(), anyDouble())).thenReturn(60.0);

            assertThatNoException().isThrownBy(() -> researchService.getAggregateMetrics(null, null));

            verify(metricsRepository).countSessionsInPeriod(any(LocalDateTime.class), any(LocalDateTime.class));
        }
    }

    @Nested
    @DisplayName("getPostureTrends()")
    class GetPostureTrends {

        @Test
        @DisplayName("Posture trends are mapped correctly from Object[]")
        void shouldMapTrendsCorrectly() {
            Object[] row1 = { Date.valueOf(LocalDate.of(2024, 5, 1)), 75.0, 10L };
            Object[] row2 = { Date.valueOf(LocalDate.of(2024, 5, 2)), 68.5, 7L  };

            when(metricsRepository.getPostureTrends(30)).thenReturn(List.of(row1, row2));

            List<PostureTrendDTO> trends = researchService.getPostureTrends(30);

            assertThat(trends).hasSize(2);
            assertThat(trends.get(0).getDate()).isEqualTo(LocalDate.of(2024, 5, 1));
            assertThat(trends.get(0).getAverageGps()).isNotNull();
            assertThat(trends.get(0).getSessionCount()).isEqualTo(10L);

            assertThat(trends.get(1).getDate()).isEqualTo(LocalDate.of(2024, 5, 2));
            assertThat(trends.get(1).getSessionCount()).isEqualTo(7L);
        }

        @Test
        @DisplayName("Empty list is returned if no data exists")
        void shouldReturnEmptyListWhenNoData() {
            when(metricsRepository.getPostureTrends(90)).thenReturn(List.of());

            assertThat(researchService.getPostureTrends(90)).isEmpty();
        }

        @Test
        @DisplayName("lastDays parameter is passed correctly to the repository")
        void shouldPassLastDaysToRepository() {
            when(metricsRepository.getPostureTrends(180)).thenReturn(List.of());

            researchService.getPostureTrends(180);

            verify(metricsRepository).getPostureTrends(180);
        }
    }

    // ─────────────────────────────────────────────────────────────
    // exportAnonymizedCsv()
    // ─────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("exportAnonymizedCsv()")
    class ExportAnonymizedCsv {

        private BiomechanicsMetrics buildMetricsForUser(LocalDate dob, Gender gender) {
            User user = new User();
            user.setDateOfBirth(dob);
            user.setGender(gender);
            user.setRole(UserRole.PATIENT);
            user.setIsActive(true);

            ScanSession session = new ScanSession();
            session.setUser(user);

            BiomechanicsMetrics m = new BiomechanicsMetrics();
            m.setScanSession(session);
            m.setQAngleLeft(BigDecimal.valueOf(12));
            m.setQAngleRight(BigDecimal.valueOf(13));
            m.setFhpAngle(BigDecimal.valueOf(5));
            m.setShoulderAsymmetryCm(BigDecimal.valueOf(1));
            m.setGlobalPostureScore(BigDecimal.valueOf(25));
            m.setRiskLevel(RiskLevel.LOW);
            return m;
        }

        @Test
        @DisplayName("Date goale - returneaza doar randul de header")
        void shouldReturnOnlyHeaderForEmptyData() {
            when(metricsRepository.findAll()).thenReturn(List.of());

            String csv = researchService.exportAnonymizedCsv(null, null);

            assertThat(csv.trim().split("\n")).hasSize(1);
            assertThat(csv).startsWith("ageGroup,gender");
        }

        @Test
        @DisplayName("Metrici cu scanSession null sunt sarite")
        void shouldSkipMetricsWithNullScanSession() {
            BiomechanicsMetrics m = new BiomechanicsMetrics();
            m.setScanSession(null);

            when(metricsRepository.findAll()).thenReturn(List.of(m));

            String csv = researchService.exportAnonymizedCsv(null, null);

            assertThat(csv.trim().split("\n")).hasSize(1);
        }

        @Test
        @DisplayName("Metrici cu user null sunt sarite")
        void shouldSkipMetricsWithNullUser() {
            ScanSession session = new ScanSession();
            session.setUser(null);

            BiomechanicsMetrics m = new BiomechanicsMetrics();
            m.setScanSession(session);

            when(metricsRepository.findAll()).thenReturn(List.of(m));

            String csv = researchService.exportAnonymizedCsv(null, null);

            assertThat(csv.trim().split("\n")).hasSize(1);
        }

        @Test
        @DisplayName("CSV contine toate grupele de varsta")
        void shouldExportAllAgeGroups() {
            List<BiomechanicsMetrics> data = List.of(
                    buildMetricsForUser(LocalDate.of(2010, 1, 1), Gender.MALE),    // UNDER_18
                    buildMetricsForUser(LocalDate.of(2000, 1, 1), Gender.FEMALE),  // 18-29
                    buildMetricsForUser(LocalDate.of(1990, 1, 1), Gender.MALE),    // 30-44
                    buildMetricsForUser(LocalDate.of(1970, 1, 1), Gender.FEMALE),  // 45-59
                    buildMetricsForUser(LocalDate.of(1950, 1, 1), Gender.MALE)     // 60_PLUS
            );
            when(metricsRepository.findAll()).thenReturn(data);

            String csv = researchService.exportAnonymizedCsv(null, null);

            assertThat(csv).contains("UNDER_18");
            assertThat(csv).contains("18-29");
            assertThat(csv).contains("30-44");
            assertThat(csv).contains("45-59");
            assertThat(csv).contains("60_PLUS");
            assertThat(csv.trim().split("\n")).hasSize(6); // header + 5 rows
        }

        @Test
        @DisplayName("Campuri null sunt exportate ca string vid")
        void shouldExportNullFieldsAsEmpty() {
            User user = new User();
            user.setDateOfBirth(LocalDate.of(1990, 1, 1));
            user.setGender(null);
            user.setRole(UserRole.PATIENT);
            user.setIsActive(true);

            ScanSession session = new ScanSession();
            session.setUser(user);

            BiomechanicsMetrics m = new BiomechanicsMetrics();
            m.setScanSession(session);
            // all metric fields null

            when(metricsRepository.findAll()).thenReturn(List.of(m));

            String csv = researchService.exportAnonymizedCsv(null, null);

            assertThat(csv).contains("UNKNOWN");
        }
    }
}
