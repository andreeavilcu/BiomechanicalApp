package com.biomechanics.backend.service;

import com.biomechanics.backend.model.entity.*;
import com.biomechanics.backend.model.enums.*;
import com.biomechanics.backend.repository.RecommendationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class RecommendationService {
    private final RecommendationRepository recommendationRepository;

    @Transactional
    public List<Recommendation> generateAndSave(ScanSession session, BiomechanicsMetrics metrics, User user) {
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

                recommendations.add(Recommendation.builder()
                        .scanSession(session)
                        .metricType(MetricType.FHP)
                        .severity(severity)
                        .title("Forward Head Posture (Anterior Head Deviation)")
                        .biomechanicalCause(
                                "Prolonged static flexed postures (e.g. desk work) reduce the force arm " +
                                        "of the extensor muscles, transferring peak load onto the posterior " +
                                        "structures and intervertebral discs. This can produce exaggerated " +
                                        "thoracic posterior curvature (kyphosis) and a 'rounded shoulders' " +
                                        "posture, a condition often associated with disc degeneration or " +
                                        "osteoporosis.")
                        .exercise(isHighRisk ? null :
                                "Chin Tucks (Cervical Retraction) to activate deep cervical flexors. " +
                                        "Gentle extensions while maintaining controlled lumbar lordosis.")
                        .ergonomicTip(
                                "Avoid prolonged static postures by introducing active breaks. " +
                                        "Adjust your monitor to eye level to reduce neck flexion.")
                        .isBlocked(isHighRisk)
                        .disclaimerRequired(severity == RecommendationSeverity.HIGH)
                        .detectedValue(String.format("%.1f°", fhp))
                        .normalRange(String.format("0° - %.1f°", normalMax))
                        .build());
            }
        }

        if (metrics.getQAngleLeft() != null && metrics.getQAngleRight() != null) {
            double qLeft = metrics.getQAngleLeft().doubleValue();
            double qRight = metrics.getQAngleRight().doubleValue();
            double qMax = Math.max(qLeft, qRight);
            double normalMin = (isMale ? 10.0 : 15.0) * ageFactor;
            double normalMax = (isMale ? 14.0 : 17.0) * ageFactor;

            if (qMax > normalMax) {
                RecommendationSeverity severity = qMax > 20.0
                        ? RecommendationSeverity.HIGH
                        : RecommendationSeverity.MODERATE;

                String genderRef = isMale ? "Males" : "Females";
                String normalRangeStr = String.format("%.0f° - %.0f°", normalMin, normalMax);

                recommendations.add(Recommendation.builder()
                        .scanSession(session)
                        .metricType(MetricType.Q_ANGLE)
                        .severity(severity)
                        .title("Elevated Q Angle" + (qMax > 20 ? " / Genu Valgum" : ""))
                        .biomechanicalCause(
                                genderRef + " have normal Q angles of " + normalRangeStr +
                                        " due to pelvic width differences. Any value exceeding " +
                                        String.format("%.0f", normalMax) + "° constitutes genu valgum " +
                                        "(knock knees) and increases lateral stress on the patellofemoral " +
                                        "joint. The common functional cause is weakness of the hip abductors, " +
                                        "which allows excessive pelvic movement and internal rotation of " +
                                        "the femur (Trendelenburg gait pattern).")
                        .exercise(isHighRisk ? null :
                                "Quadriceps isometric exercises and gluteus medius strengthening. " +
                                        "Knee stabilization drills with resistance bands.")
                        .ergonomicTip(isHighRisk
                                ? "Deep squats are STRICTLY contraindicated. High-impact dynamic " +
                                "exercises are blocked due to elevated shear forces on the knee."
                                : "Avoid deep squats and running on hard surfaces until correction " +
                                "is achieved.")
                        .isBlocked(isHighRisk)
                        .disclaimerRequired(severity == RecommendationSeverity.HIGH)
                        .detectedValue(String.format("L:%.1f° / R:%.1f°", qLeft, qRight))
                        .normalRange(normalRangeStr)
                        .build());
            }
        }

        if (metrics.getShoulderAsymmetryCm() != null) {
            double asym = metrics.getShoulderAsymmetryCm().doubleValue();

            if (asym > 1.5) {
                RecommendationSeverity severity = asym > 3.0
                        ? RecommendationSeverity.HIGH
                        : RecommendationSeverity.MODERATE;

                recommendations.add(Recommendation.builder()
                        .scanSession(session)
                        .metricType(MetricType.SHOULDER_ASYMMETRY)
                        .severity(severity)
                        .title("Shoulder Asymmetry / Scapular Imbalance")
                        .biomechanicalCause(
                                "Somatic asymmetry can be functional, often resulting from " +
                                        "predominant use of a single dominant limb. Biomechanically, a " +
                                        "shoulder level difference frequently arises from asymmetric " +
                                        "scapular elevation and sustained upper trapezius contraction, " +
                                        "or from a more significant spinal malalignment such as scoliosis " +
                                        "(lateral deviation in a C or S pattern). Hip abductor weakness " +
                                        "can create asymmetric pelvic tilt that propagates upward through " +
                                        "the kinetic chain.")
                        .exercise(isHighRisk ? null :
                                "Unilateral stretching to release the upper trapezius on the " +
                                        "elevated side. Strengthening exercises to balance the scapular " +
                                        "force couple.")
                        .ergonomicTip(
                                "Avoid carrying weight asymmetrically (e.g. backpack on one " +
                                        "shoulder). A full kinetic chain assessment is recommended, " +
                                        "including pelvic tilt evaluation.")
                        .isBlocked(isHighRisk)
                        .disclaimerRequired(severity == RecommendationSeverity.HIGH)
                        .detectedValue(String.format("%.1f cm", asym))
                        .normalRange("≤ 1.5 cm")
                        .build());
            }
        }

        String globalTitle;
        String globalCause;
        String globalTip;

        if (gps <= 20.0) {
            globalTitle = "Optimal Biomechanics";
            globalCause =
                    "All parameters fall within normal physiological limits. " +
                            "No significant postural deviations were detected.";
            globalTip =
                    "Maintain your current physical activity routine. " +
                            "A follow-up scan is recommended in 90 days to track stability.";

        } else if (gps <= 50.0) {
            globalTitle = "Functional Postural Deviations Detected";
            globalCause =
                    "Postural deviations have been detected that can be corrected " +
                            "through targeted exercises. Active breaks and stretching routines " +
                            "are recommended.";
            globalTip =
                    "Follow the specific exercises recommended for each deviated metric. " +
                            "Re-evaluation is recommended in 30 days to assess progress.";

        } else {
            globalTitle = "Biomechanical Risk Alert";
            globalCause =
                    "Detected values indicate possible structural or pathological changes. " +
                            "This application does not replace medical diagnosis.";
            globalTip =
                    "Consultation with a physiotherapist or rehabilitation specialist is " +
                            "strongly recommended. High-impact dynamic exercises have been blocked " +
                            "to prevent further injury.";

            for (Recommendation r : recommendations) {
                r.setIsBlocked(true);
                r.setExercise(null);
                r.setDisclaimerRequired(true);
            }
        }

        RecommendationSeverity globalSeverity;
        if (gps <= 20.0) {
            globalSeverity = RecommendationSeverity.LOW;
        } else if (gps <= 50.0) {
            globalSeverity = RecommendationSeverity.MODERATE;
        } else {
            globalSeverity = RecommendationSeverity.HIGH;
        }

        recommendations.add(Recommendation.builder()
                .scanSession(session)
                .metricType(MetricType.GLOBAL)
                .severity(globalSeverity)
                .title(globalTitle)
                .biomechanicalCause(globalCause)
                .exercise(null)
                .ergonomicTip(globalTip)
                .isBlocked(isHighRisk)
                .disclaimerRequired(isHighRisk)
                .detectedValue(String.format("GPS: %.1f%%", gps))
                .normalRange("0% - 20%")
                .build());

        List<Recommendation> saved = recommendationRepository.saveAll(recommendations);
        log.info("Generated and saved {} recommendations for session ID={} (GPS={}, Risk={})",
                saved.size(), session.getId(),
                String.format("%.1f", gps), metrics.getRiskLevel());

        return saved;
    }

}
