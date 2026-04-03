package com.biomechanics.backend.service;

import com.biomechanics.backend.model.entity.*;
import com.biomechanics.backend.model.enums.*;
import com.biomechanics.backend.repository.RecommendationRepository;
import com.biomechanics.backend.repository.RecommendationTemplateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class RecommendationService {
    private final RecommendationRepository recommendationRepository;
    private final RecommendationTemplateRepository templateRepository;

    @Transactional
    public List<Recommendation> generateAndSave(
            ScanSession session,
            BiomechanicsMetrics metrics,
            User user
    ) {
        List<Recommendation> recommendations = new ArrayList<>();

        double gps = metrics.getGlobalPostureScore().doubleValue();
        boolean isHighRisk = metrics.getRiskLevel() == RiskLevel.HIGH;
        boolean isMale = user.getGender() == Gender.MALE;
        int age = user.getAge();
        double ageFactor = age > 60 ? 0.85 : 1.0;

        if (metrics.getFhpAngle() != null) {
            double fhp = metrics.getFhpAngle().doubleValue();
            double normalMax = 5.0 * ageFactor;

            if (fhp > normalMax) {
                RecommendationSeverity severity = fhp > 20.0
                        ? RecommendationSeverity.HIGH
                        : RecommendationSeverity.MODERATE;

                buildFromTemplate(MetricType.FHP, severity, isHighRisk, session,
                        String.format("%.1f°", fhp),
                        String.format("0° - %.1f°", normalMax),
                        isMale
                ).ifPresent(recommendations::add);
            }
        }

        if (metrics.getQAngleLeft() != null && metrics.getQAngleRight() != null) {
            double qLeft = metrics.getQAngleLeft().doubleValue();
            double qRight = metrics.getQAngleRight().doubleValue();
            double qMax = Math.max(qLeft, qRight);
            double normalMax = (isMale ? 14.0 : 17.0) * ageFactor;

            if (qMax > normalMax) {
                RecommendationSeverity severity = qMax > 20.0
                        ? RecommendationSeverity.HIGH
                        : RecommendationSeverity.MODERATE;

                double normalMin = (isMale ? 10.0 : 15.0) * ageFactor;
                String normalRange = String.format("%.0f° - %.0f°", normalMin, normalMax);

                buildFromTemplate(MetricType.Q_ANGLE, severity, isHighRisk, session,
                        String.format("L:%.1f° / R:%.1f°", qLeft, qRight),
                        normalRange,
                        isMale
                ).ifPresent(recommendations::add);
            }
        }

        if (metrics.getShoulderAsymmetryCm() != null) {
            double asym = metrics.getShoulderAsymmetryCm().doubleValue();

            if (asym > 1.5) {
                RecommendationSeverity severity = asym > 3.0
                        ? RecommendationSeverity.HIGH
                        : RecommendationSeverity.MODERATE;

                buildFromTemplate(MetricType.SHOULDER_ASYMMETRY, severity, isHighRisk, session,
                        String.format("%.1f cm", asym),
                        "≤ 1.5 cm",
                        isMale
                ).ifPresent(recommendations::add);
            }
        }

        RecommendationSeverity globalSeverity;
        if (gps <= 20.0) {
            globalSeverity = RecommendationSeverity.LOW;
        } else if (gps <= 50.0) {
            globalSeverity = RecommendationSeverity.MODERATE;
        } else {
            globalSeverity = RecommendationSeverity.HIGH;


            for (Recommendation r : recommendations) {
                r.setIsBlocked(true);
                r.setExercise(null);
                r.setDisclaimerRequired(true);
            }
        }

        buildFromTemplate(MetricType.GLOBAL, globalSeverity, isHighRisk, session,
                String.format("GPS: %.1f%%", gps),
                "0% - 20%",
                isMale
        ).ifPresent(recommendations::add);

        List<Recommendation> saved = recommendationRepository.saveAll(recommendations);
        log.info("Generated and saved {} recommendations for session ID={} (GPS={}, Risk={})",
                saved.size(), session.getId(),
                String.format("%.1f", gps), metrics.getRiskLevel());

        return saved;
    }

    private Optional<Recommendation> buildFromTemplate(
            MetricType metricType,
            RecommendationSeverity severity,
            boolean isHighRisk,
            ScanSession session,
            String detectedValue,
            String normalRange,
            boolean isMale
    ) {
        Optional<RecommendationTemplate> templateOpt =
                templateRepository.findByMetricTypeAndSeverity(metricType, severity);

        if (templateOpt.isEmpty()) {
            log.warn("No recommendation template found for metric={}, severity={}",
                    metricType, severity);
            return Optional.empty();
        }

        RecommendationTemplate t = templateOpt.get();

        String exercise = isHighRisk ? null : t.getExercise();
        String ergonomicTip = (isHighRisk && t.getBlockedErgonomicTip() != null)
                ? t.getBlockedErgonomicTip()
                : t.getErgonomicTip();

        String templateNormalRange = isMale
                ? (t.getNormalRangeMale() != null ? t.getNormalRangeMale() : normalRange)
                : (t.getNormalRangeFemale() != null ? t.getNormalRangeFemale() : normalRange);

        return Optional.of(Recommendation.builder()
                .scanSession(session)
                .metricType(metricType)
                .severity(severity)
                .title(t.getTitle())
                .biomechanicalCause(t.getBiomechanicalCause())
                .exercise(exercise)
                .ergonomicTip(ergonomicTip)
                .isBlocked(isHighRisk)
                .disclaimerRequired(severity == RecommendationSeverity.HIGH)
                .detectedValue(detectedValue)
                .normalRange(templateNormalRange)
                .build());
    }

}
