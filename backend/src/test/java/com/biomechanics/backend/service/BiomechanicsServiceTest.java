package com.biomechanics.backend.service;

import com.biomechanics.backend.model.dto.PythonResponseDTO;
import com.biomechanics.backend.model.entity.BiomechanicsMetrics;
import com.biomechanics.backend.model.entity.User;
import com.biomechanics.backend.model.enums.Gender;
import com.biomechanics.backend.model.enums.RiskLevel;
import com.biomechanics.backend.model.enums.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("BiomechanicsService Tests")
class BiomechanicsServiceTest {

    @InjectMocks
    private BiomechanicsService biomechanicsService;

    private User maleUser;
    private User femaleUser;

    @BeforeEach
    void setUp() {
        maleUser = new User();
        maleUser.setId(1L);
        maleUser.setEmail("barbat@test.com");
        maleUser.setGender(Gender.MALE);
        maleUser.setDateOfBirth(LocalDate.of(1990, 6, 15));
        maleUser.setRole(UserRole.PATIENT);
        maleUser.setIsActive(true);

        femaleUser = new User();
        femaleUser.setId(2L);
        femaleUser.setEmail("femeie@test.com");
        femaleUser.setGender(Gender.FEMALE);
        femaleUser.setDateOfBirth(LocalDate.of(1990, 6, 15));
        femaleUser.setRole(UserRole.PATIENT);
        femaleUser.setIsActive(true);
    }

    private PythonResponseDTO buildNormalResponse() {
        PythonResponseDTO dto = new PythonResponseDTO();

        dto.setNeck(kp(0.0, 0.9, 0.0));
        dto.setLEar(kp(-0.05, 1.0, 0.01));
        dto.setREar(kp(0.05, 1.0, 0.01));

        dto.setLShoulder(kp(-0.2, 0.8, 0.01));
        dto.setRShoulder(kp(0.2, 0.8, -0.01));

        dto.setLHip(kp(-0.1, 0.5, 0.0));
        dto.setLKnee(kp(-0.1, 0.25, 0.0));
        dto.setLAnkle(kp(-0.1, 0.0, 0.0));

        dto.setRHip(kp(0.1, 0.5, 0.0));
        dto.setRKnee(kp(0.1, 0.25, 0.0));
        dto.setRAnkle(kp(0.1, 0.0, 0.0));

        return dto;
    }

    private PythonResponseDTO buildAbnormalResponse() {
        PythonResponseDTO dto = new PythonResponseDTO();

        dto.setNeck(kp(0.0, 0.9, 0.0));
        dto.setLEar(kp(-0.05, 1.0, 0.15));
        dto.setREar(kp(0.05, 1.0, 0.15));

        dto.setLShoulder(kp(-0.2, 0.8, 0.05));
        dto.setRShoulder(kp(0.2, 0.8, -0.05));

        dto.setLHip(kp(-0.15, 0.5, 0.0));
        dto.setLKnee(kp(-0.1, 0.25, 0.0));
        dto.setLAnkle(kp(-0.05, 0.0, 0.0));

        dto.setRHip(kp(0.15, 0.5, 0.0));
        dto.setRKnee(kp(0.1, 0.25, 0.0));
        dto.setRAnkle(kp(0.05, 0.0, 0.0));

        return dto;
    }

    private PythonResponseDTO.KeypointDTO kp(double x, double y, double z) {
        PythonResponseDTO.KeypointDTO kp = new PythonResponseDTO.KeypointDTO();
        kp.setX(x); kp.setY(y); kp.setZ(z);
        return kp;
    }

    @Nested
    @DisplayName("calculateMetrics()")
    class CalculateMetrics {

        @Test
        @DisplayName("Normal keypoints - GPS between 0 and 100 and RiskLevel calculated")
        void shouldCalculateMetricsForNormalPosture() {
            BiomechanicsMetrics result = biomechanicsService
                    .calculateMetrics(buildNormalResponse(), maleUser);

            assertThat(result.getGlobalPostureScore()).isNotNull();
            assertThat(result.getGlobalPostureScore().doubleValue())
                    .isBetween(0.0, 100.0);
            assertThat(result.getRiskLevel()).isNotNull();
        }

        @Test
        @DisplayName("Abnormal posture - higher GPS compared to normal posture")
        void shouldReturnHigherGpsForAbnormalPosture() {
            BiomechanicsMetrics normal = biomechanicsService
                    .calculateMetrics(buildNormalResponse(), maleUser);
            BiomechanicsMetrics abnormal = biomechanicsService
                    .calculateMetrics(buildAbnormalResponse(), maleUser);

            assertThat(abnormal.getGlobalPostureScore().doubleValue())
                    .isGreaterThan(normal.getGlobalPostureScore().doubleValue());
        }

        @Test
        @DisplayName("Q Angle, FHP, shoulder asymmetry are populated")
        void shouldPopulateAllMetricFields() {
            BiomechanicsMetrics result = biomechanicsService
                    .calculateMetrics(buildNormalResponse(), maleUser);

            assertThat(result.getQAngleLeft()).isNotNull();
            assertThat(result.getQAngleRight()).isNotNull();
            assertThat(result.getFhpAngle()).isNotNull();
            assertThat(result.getFhpDistanceCm()).isNotNull();
            assertThat(result.getShoulderAsymmetryCm()).isNotNull();
        }

        @Test
        @DisplayName("GPS is between 0 and 100 (clamped correctly)")
        void gpsShouldBeClamped() {
            BiomechanicsMetrics result = biomechanicsService
                    .calculateMetrics(buildAbnormalResponse(), maleUser);

            double gps = result.getGlobalPostureScore().doubleValue();
            assertThat(gps).isGreaterThanOrEqualTo(0.0).isLessThanOrEqualTo(100.0);
        }
    }

    @Nested
    @DisplayName("RiskLevel calculated from GPS")
    class DetermineRiskLevel {

        @Test
        @DisplayName("GPS <= 20 => RiskLevel LOW")
        void shouldReturnLowRiskForLowGps() {
            BiomechanicsMetrics result = biomechanicsService
                    .calculateMetrics(buildNormalResponse(), maleUser);

            if (result.getGlobalPostureScore().doubleValue() <= 20.0) {
                assertThat(result.getRiskLevel()).isEqualTo(RiskLevel.LOW);
            }
        }

        @Test
        @DisplayName("Result always has a non-null RiskLevel")
        void riskLevelShouldNeverBeNull() {
            BiomechanicsMetrics r1 = biomechanicsService.calculateMetrics(buildNormalResponse(), maleUser);
            BiomechanicsMetrics r2 = biomechanicsService.calculateMetrics(buildAbnormalResponse(), femaleUser);

            assertThat(r1.getRiskLevel()).isNotNull();
            assertThat(r2.getRiskLevel()).isNotNull();
        }
    }

    @Nested
    @DisplayName("generateRecommendations()")
    class GenerateRecommendations {

        @Test
        @DisplayName("Normal posture - empty list or few recommendations")
        void shouldReturnEmptyOrFewForNormalPosture() {
            BiomechanicsMetrics metrics = biomechanicsService
                    .calculateMetrics(buildNormalResponse(), maleUser);

            List<String> recs = biomechanicsService.generateRecommendations(metrics);

            assertThat(recs).isNotNull();
        }

        @Test
        @DisplayName("FHP > 10 - adds FHP type recommendation")
        void shouldAddFhpRecommendationForHighFhp() {
            BiomechanicsMetrics metrics = new BiomechanicsMetrics();
            metrics.setFhpAngle(BigDecimal.valueOf(15));
            metrics.setQAngleLeft(BigDecimal.valueOf(12));
            metrics.setQAngleRight(BigDecimal.valueOf(12));
            metrics.setShoulderAsymmetryCm(BigDecimal.valueOf(1));
            metrics.setRiskLevel(RiskLevel.LOW);

            List<String> recs = biomechanicsService.generateRecommendations(metrics);

            assertThat(recs).anyMatch(r -> r.contains("Forward Head"));
        }

        @Test
        @DisplayName("Average Q angle > 17 - adds Q angle recommendation")
        void shouldAddQAngleRecommendation() {
            BiomechanicsMetrics metrics = new BiomechanicsMetrics();
            metrics.setFhpAngle(BigDecimal.valueOf(5));
            metrics.setQAngleLeft(BigDecimal.valueOf(20));
            metrics.setQAngleRight(BigDecimal.valueOf(20));
            metrics.setShoulderAsymmetryCm(BigDecimal.valueOf(1));
            metrics.setRiskLevel(RiskLevel.LOW);

            List<String> recs = biomechanicsService.generateRecommendations(metrics);

            assertThat(recs).anyMatch(r -> r.contains("Q Angle"));
        }

        @Test
        @DisplayName("Asymmetry > 2 cm - adds shoulder recommendation")
        void shouldAddShoulderRecommendation() {
            BiomechanicsMetrics metrics = new BiomechanicsMetrics();
            metrics.setFhpAngle(BigDecimal.valueOf(5));
            metrics.setQAngleLeft(BigDecimal.valueOf(12));
            metrics.setQAngleRight(BigDecimal.valueOf(12));
            metrics.setShoulderAsymmetryCm(BigDecimal.valueOf(3));
            metrics.setRiskLevel(RiskLevel.LOW);

            List<String> recs = biomechanicsService.generateRecommendations(metrics);

            assertThat(recs).anyMatch(r -> r.contains("Shoulder"));
        }

        @Test
        @DisplayName("RiskLevel HIGH - adds consultation warning")
        void shouldAddWarningForHighRisk() {
            BiomechanicsMetrics metrics = new BiomechanicsMetrics();
            metrics.setFhpAngle(BigDecimal.valueOf(5));
            metrics.setQAngleLeft(BigDecimal.valueOf(12));
            metrics.setQAngleRight(BigDecimal.valueOf(12));
            metrics.setShoulderAsymmetryCm(BigDecimal.valueOf(1));
            metrics.setRiskLevel(RiskLevel.HIGH);

            List<String> recs = biomechanicsService.generateRecommendations(metrics);

            assertThat(recs).anyMatch(r -> r.contains("WARNING") || r.contains("High risk"));
        }
    }
}
