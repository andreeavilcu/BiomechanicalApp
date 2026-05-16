package com.biomechanics.backend.service;

import com.biomechanics.backend.model.entity.*;
import com.biomechanics.backend.model.enums.*;
import com.biomechanics.backend.repository.RecommendationRepository;
import com.biomechanics.backend.repository.RecommendationTemplateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("RecommendationService Tests")
class RecommendationServiceTest {

    @Mock private RecommendationRepository recommendationRepository;
    @Mock private RecommendationTemplateRepository templateRepository;

    @InjectMocks
    private RecommendationService recommendationService;

    private ScanSession session;
    private User user;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(1L);
        user.setEmail("pac@test.com");
        user.setGender(Gender.MALE);
        user.setDateOfBirth(LocalDate.of(1990, 1, 1));
        user.setRole(UserRole.PATIENT);
        user.setIsActive(true);

        session = new ScanSession();
        session.setId(1L);
        session.setUser(user);
    }

    private BiomechanicsMetrics buildMetrics(double gps, RiskLevel risk,
                                              Double fhp, Double qLeft, Double qRight,
                                              Double shoulderAsym) {
        BiomechanicsMetrics m = new BiomechanicsMetrics();
        m.setGlobalPostureScore(BigDecimal.valueOf(gps));
        m.setRiskLevel(risk);
        if (fhp != null)          m.setFhpAngle(BigDecimal.valueOf(fhp));
        if (qLeft != null)        m.setQAngleLeft(BigDecimal.valueOf(qLeft));
        if (qRight != null)       m.setQAngleRight(BigDecimal.valueOf(qRight));
        if (shoulderAsym != null) m.setShoulderAsymmetryCm(BigDecimal.valueOf(shoulderAsym));
        return m;
    }

    private RecommendationTemplate buildTemplate(MetricType type, RecommendationSeverity sev) {
        return RecommendationTemplate.builder()
                .metricType(type)
                .severity(sev)
                .title("Titlu test")
                .biomechanicalCause("Cauza test")
                .exercise("Exercitiu test")
                .ergonomicTip("Sfat ergonomic")
                .build();
    }

    @Nested
    @DisplayName("generateAndSave() - FHP")
    class FhpRecommendations {

        @Test
        @DisplayName("FHP within normal limits - does not generate FHP recommendation")
        void shouldNotGenerateFhpRecommendationForNormalAngle() {
            BiomechanicsMetrics metrics = buildMetrics(10, RiskLevel.LOW, 3.0, 12.0, 12.0, 1.0);
            when(templateRepository.findByMetricTypeAndSeverity(eq(MetricType.GLOBAL), any()))
                    .thenReturn(Optional.of(buildTemplate(MetricType.GLOBAL, RecommendationSeverity.LOW)));
            when(recommendationRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

            recommendationService.generateAndSave(session, metrics, user);

            verify(templateRepository, never())
                    .findByMetricTypeAndSeverity(eq(MetricType.FHP), any());
        }

        @Test
        @DisplayName("Moderate FHP (6-20°) - generates MODERATE recommendation")
        void shouldGenerateModerateFhpRecommendation() {
            BiomechanicsMetrics metrics = buildMetrics(15, RiskLevel.LOW, 10.0, 12.0, 12.0, 1.0);

            when(templateRepository.findByMetricTypeAndSeverity(MetricType.FHP, RecommendationSeverity.MODERATE))
                    .thenReturn(Optional.of(buildTemplate(MetricType.FHP, RecommendationSeverity.MODERATE)));
            when(templateRepository.findByMetricTypeAndSeverity(eq(MetricType.GLOBAL), any()))
                    .thenReturn(Optional.of(buildTemplate(MetricType.GLOBAL, RecommendationSeverity.LOW)));
            when(recommendationRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

            List<Recommendation> result = recommendationService.generateAndSave(session, metrics, user);

            assertThat(result).anyMatch(r -> r.getMetricType() == MetricType.FHP
                    && r.getSeverity() == RecommendationSeverity.MODERATE);
        }

        @Test
        @DisplayName("Severe FHP (>20°) - generates HIGH recommendation")
        void shouldGenerateHighFhpRecommendation() {
            BiomechanicsMetrics metrics = buildMetrics(30, RiskLevel.LOW, 25.0, 12.0, 12.0, 1.0);

            when(templateRepository.findByMetricTypeAndSeverity(MetricType.FHP, RecommendationSeverity.HIGH))
                    .thenReturn(Optional.of(buildTemplate(MetricType.FHP, RecommendationSeverity.HIGH)));
            when(templateRepository.findByMetricTypeAndSeverity(eq(MetricType.GLOBAL), any()))
                    .thenReturn(Optional.of(buildTemplate(MetricType.GLOBAL, RecommendationSeverity.MODERATE)));
            when(recommendationRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

            List<Recommendation> result = recommendationService.generateAndSave(session, metrics, user);

            assertThat(result).anyMatch(r -> r.getMetricType() == MetricType.FHP
                    && r.getSeverity() == RecommendationSeverity.HIGH);
        }
    }

    @Nested
    @DisplayName("generateAndSave() - Shoulder Asymmetry")
    class ShoulderRecommendations {

        @Test
        @DisplayName("Asymmetry <= 1.5 cm - does not generate recommendation")
        void shouldNotGenerateForNormalAsymmetry() {
            BiomechanicsMetrics metrics = buildMetrics(10, RiskLevel.LOW, 3.0, 12.0, 12.0, 1.0);

            when(templateRepository.findByMetricTypeAndSeverity(eq(MetricType.GLOBAL), any()))
                    .thenReturn(Optional.of(buildTemplate(MetricType.GLOBAL, RecommendationSeverity.LOW)));
            when(recommendationRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

            recommendationService.generateAndSave(session, metrics, user);

            verify(templateRepository, never())
                    .findByMetricTypeAndSeverity(eq(MetricType.SHOULDER_ASYMMETRY), any());
        }

        @Test
        @DisplayName("Asymmetry > 3.0 cm - generates HIGH recommendation")
        void shouldGenerateHighForLargeAsymmetry() {
            BiomechanicsMetrics metrics = buildMetrics(15, RiskLevel.LOW, 3.0, 12.0, 12.0, 4.0);

            when(templateRepository.findByMetricTypeAndSeverity(MetricType.SHOULDER_ASYMMETRY, RecommendationSeverity.HIGH))
                    .thenReturn(Optional.of(buildTemplate(MetricType.SHOULDER_ASYMMETRY, RecommendationSeverity.HIGH)));
            when(templateRepository.findByMetricTypeAndSeverity(eq(MetricType.GLOBAL), any()))
                    .thenReturn(Optional.of(buildTemplate(MetricType.GLOBAL, RecommendationSeverity.LOW)));
            when(recommendationRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

            List<Recommendation> result = recommendationService.generateAndSave(session, metrics, user);

            assertThat(result).anyMatch(r -> r.getMetricType() == MetricType.SHOULDER_ASYMMETRY);
        }
    }

    @Nested
    @DisplayName("generateAndSave() - global GPS score")
    class GlobalSeverity {

        @Test
        @DisplayName("GPS <= 20 - LOW global recommendation")
        void shouldGenerateLowGlobalSeverity() {
            BiomechanicsMetrics metrics = buildMetrics(15, RiskLevel.LOW, 3.0, 12.0, 12.0, 1.0);

            when(templateRepository.findByMetricTypeAndSeverity(MetricType.GLOBAL, RecommendationSeverity.LOW))
                    .thenReturn(Optional.of(buildTemplate(MetricType.GLOBAL, RecommendationSeverity.LOW)));
            when(recommendationRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

            List<Recommendation> result = recommendationService.generateAndSave(session, metrics, user);

            assertThat(result).anyMatch(r -> r.getMetricType() == MetricType.GLOBAL
                    && r.getSeverity() == RecommendationSeverity.LOW);
        }

        @Test
        @DisplayName("GPS > 50 - existing recommendations are blocked")
        void shouldBlockRecommendationsForHighGps() {
            BiomechanicsMetrics metrics = buildMetrics(60, RiskLevel.HIGH, 25.0, 12.0, 12.0, 1.0);

            when(templateRepository.findByMetricTypeAndSeverity(MetricType.FHP, RecommendationSeverity.HIGH))
                    .thenReturn(Optional.of(buildTemplate(MetricType.FHP, RecommendationSeverity.HIGH)));
            when(templateRepository.findByMetricTypeAndSeverity(MetricType.GLOBAL, RecommendationSeverity.HIGH))
                    .thenReturn(Optional.of(buildTemplate(MetricType.GLOBAL, RecommendationSeverity.HIGH)));
            when(recommendationRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<Recommendation>> captor = ArgumentCaptor.forClass(List.class);
            recommendationService.generateAndSave(session, metrics, user);
            verify(recommendationRepository).saveAll(captor.capture());

            List<Recommendation> saved = captor.getValue();
            saved.stream()
                    .filter(r -> r.getMetricType() != MetricType.GLOBAL)
                    .forEach(r -> assertThat(r.getIsBlocked()).isTrue());
        }

        @Test
        @DisplayName("Missing template - recommendation is not added, no exception")
        void shouldSkipWhenTemplateNotFound() {
            BiomechanicsMetrics metrics = buildMetrics(10, RiskLevel.LOW, 10.0, 12.0, 12.0, 1.0);

            when(templateRepository.findByMetricTypeAndSeverity(any(), any()))
                    .thenReturn(Optional.empty());
            when(recommendationRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

            assertThatNoException().isThrownBy(() ->
                    recommendationService.generateAndSave(session, metrics, user));
        }
    }

    @Nested
    @DisplayName("generateAndSave() - high risk")
    class HighRisk {

        @Test
        @DisplayName("HIGH Risk - exercise is null in recommendation")
        void shouldNullifyExerciseForHighRisk() {
            BiomechanicsMetrics metrics = buildMetrics(10, RiskLevel.HIGH, 10.0, 12.0, 12.0, 1.0);

            RecommendationTemplate template = buildTemplate(MetricType.FHP, RecommendationSeverity.MODERATE);
            template.setExercise("Exercitiu de testare");

            when(templateRepository.findByMetricTypeAndSeverity(MetricType.FHP, RecommendationSeverity.MODERATE))
                    .thenReturn(Optional.of(template));
            when(templateRepository.findByMetricTypeAndSeverity(eq(MetricType.GLOBAL), any()))
                    .thenReturn(Optional.of(buildTemplate(MetricType.GLOBAL, RecommendationSeverity.LOW)));
            when(recommendationRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<Recommendation>> captor = ArgumentCaptor.forClass(List.class);
            recommendationService.generateAndSave(session, metrics, user);
            verify(recommendationRepository).saveAll(captor.capture());

            captor.getValue().stream()
                    .filter(r -> r.getMetricType() == MetricType.FHP)
                    .forEach(r -> assertThat(r.getExercise()).isNull());
        }
    }

    @Nested
    @DisplayName("generateAndSave() - Q Angle")
    class QAngleRecommendations {

        @Test
        @DisplayName("Q angle above male limit (>14°) - generates MODERATE recommendation")
        void shouldGenerateModerateQAngleForMale() {
            BiomechanicsMetrics metrics = buildMetrics(15, RiskLevel.LOW, 3.0, 18.0, 18.0, 1.0);

            when(templateRepository.findByMetricTypeAndSeverity(MetricType.Q_ANGLE, RecommendationSeverity.MODERATE))
                    .thenReturn(Optional.of(buildTemplate(MetricType.Q_ANGLE, RecommendationSeverity.MODERATE)));
            when(templateRepository.findByMetricTypeAndSeverity(eq(MetricType.GLOBAL), any()))
                    .thenReturn(Optional.of(buildTemplate(MetricType.GLOBAL, RecommendationSeverity.LOW)));
            when(recommendationRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

            List<Recommendation> result = recommendationService.generateAndSave(session, metrics, user);

            assertThat(result).anyMatch(r -> r.getMetricType() == MetricType.Q_ANGLE
                    && r.getSeverity() == RecommendationSeverity.MODERATE);
        }

        @Test
        @DisplayName("Q angle within normal limits for male - does not generate Q_ANGLE recommendation")
        void shouldNotGenerateQAngleForNormalMale() {
            BiomechanicsMetrics metrics = buildMetrics(10, RiskLevel.LOW, 3.0, 12.0, 12.0, 1.0);

            when(templateRepository.findByMetricTypeAndSeverity(eq(MetricType.GLOBAL), any()))
                    .thenReturn(Optional.of(buildTemplate(MetricType.GLOBAL, RecommendationSeverity.LOW)));
            when(recommendationRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

            recommendationService.generateAndSave(session, metrics, user);

            verify(templateRepository, never())
                    .findByMetricTypeAndSeverity(eq(MetricType.Q_ANGLE), any());
        }
    }

    @Nested
    @DisplayName("generateAndSave() - female user")
    class FemaleUser {

        @Test
        @DisplayName("Female with Q angle above female limit (>17°) - generates recommendation")
        void shouldGenerateQAngleForFemaleExceedingLimit() {
            User femaleUser = new User();
            femaleUser.setId(2L);
            femaleUser.setEmail("pac2@test.com");
            femaleUser.setGender(Gender.FEMALE);
            femaleUser.setDateOfBirth(LocalDate.of(1990, 1, 1));
            femaleUser.setRole(UserRole.PATIENT);
            femaleUser.setIsActive(true);

            BiomechanicsMetrics metrics = buildMetrics(15, RiskLevel.LOW, 3.0, 20.0, 20.0, 1.0);

            when(templateRepository.findByMetricTypeAndSeverity(MetricType.Q_ANGLE, RecommendationSeverity.MODERATE))
                    .thenReturn(Optional.of(buildTemplate(MetricType.Q_ANGLE, RecommendationSeverity.MODERATE)));
            when(templateRepository.findByMetricTypeAndSeverity(eq(MetricType.GLOBAL), any()))
                    .thenReturn(Optional.of(buildTemplate(MetricType.GLOBAL, RecommendationSeverity.LOW)));
            when(recommendationRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

            List<Recommendation> result = recommendationService.generateAndSave(session, metrics, femaleUser);

            assertThat(result).anyMatch(r -> r.getMetricType() == MetricType.Q_ANGLE);
        }
    }

    @Nested
    @DisplayName("generateAndSave() - user age > 60")
    class ElderlyUser {

        @Test
        @DisplayName("User > 60 years old: normalMax FHP is reduced by ageFactor 0.85")
        void shouldApplyAgeFactorForElderlyUser() {
            User elderlyUser = new User();
            elderlyUser.setId(3L);
            elderlyUser.setEmail("varstnic@test.com");
            elderlyUser.setGender(Gender.MALE);
            elderlyUser.setDateOfBirth(LocalDate.of(1950, 1, 1));
            elderlyUser.setRole(UserRole.PATIENT);
            elderlyUser.setIsActive(true);

            BiomechanicsMetrics metrics = buildMetrics(15, RiskLevel.LOW, 5.0, 9.0, 9.0, 1.0);

            when(templateRepository.findByMetricTypeAndSeverity(MetricType.FHP, RecommendationSeverity.MODERATE))
                    .thenReturn(Optional.of(buildTemplate(MetricType.FHP, RecommendationSeverity.MODERATE)));
            when(templateRepository.findByMetricTypeAndSeverity(eq(MetricType.GLOBAL), any()))
                    .thenReturn(Optional.of(buildTemplate(MetricType.GLOBAL, RecommendationSeverity.LOW)));
            when(recommendationRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

            List<Recommendation> result = recommendationService.generateAndSave(session, metrics, elderlyUser);

            assertThat(result).anyMatch(r -> r.getMetricType() == MetricType.FHP);
        }
    }
}
