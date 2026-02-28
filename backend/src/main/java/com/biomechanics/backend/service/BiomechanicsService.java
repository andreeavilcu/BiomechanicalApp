package com.biomechanics.backend.service;

import com.biomechanics.backend.model.dto.PythonResponseDTO;
import com.biomechanics.backend.model.entity.BiomechanicsMetrics;
import com.biomechanics.backend.model.entity.RawKeypoints;
import com.biomechanics.backend.model.entity.User;
import com.biomechanics.backend.model.enums.RiskLevel;
import com.biomechanics.backend.util.Vector3D;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class BiomechanicsService {

    public BiomechanicsMetrics calculateMetrics(PythonResponseDTO pythonResponse, User user) {
        log.info("Calculating biomechanics metrics for user {}", user.getEmail());

        BiomechanicsMetrics metrics = new BiomechanicsMetrics();

        try {
            BigDecimal qAngleLeft = calculateQAngle (
                    pythonResponse.getLHip(),
                    pythonResponse.getLKnee(),
                    pythonResponse.getLAnkle(),
                    "LEFT"
            );
            metrics.setQAngleLeft(qAngleLeft);


            BigDecimal qAngleRight = calculateQAngle(
                    pythonResponse.getRHip(),
                    pythonResponse.getRKnee(),
                    pythonResponse.getRAnkle(),
                    "RIGHT"
            );
            metrics.setQAngleRight(qAngleRight);

            ForwardHeadResult fhpResult = calculateForwardHeadPosture(
                    pythonResponse.getNeck(),
                    pythonResponse.getLEar(),
                    pythonResponse.getREar()
            );
            metrics.setFhpAngle(fhpResult.angle);
            metrics.setFhpDistanceCm(fhpResult.distanceCm);

            BigDecimal shoulderAsymmetry = calculateShoulderAsymmetry(
                    pythonResponse.getLShoulder(),
                    pythonResponse.getRShoulder()
            );
            metrics.setShoulderAsymmetryCm(shoulderAsymmetry);

            BigDecimal gps = calculateGlobalPostureScore(
                    qAngleLeft, qAngleRight,
                    fhpResult.angle,
                    shoulderAsymmetry,
                    user
            );
            metrics.setGlobalPostureScore(gps);

            RiskLevel riskLevel = determineRiskLevel(gps);
            metrics.setRiskLevel(riskLevel);

            log.info("Metrics calculated successfully. GPS: {}, Risk: {}", gps, riskLevel);


        } catch (Exception e) {
            log.error("Error calculating metrics: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to calculate biomechanics metrics", e);
        }

        return metrics;
    }

    private BigDecimal calculateQAngle(
            PythonResponseDTO.KeypointDTO hip,
            PythonResponseDTO.KeypointDTO knee,
            PythonResponseDTO.KeypointDTO ankle,
            String side
    ){
        log.debug("Calculating Q angle for {} leg", side);

        Vector3D hipPoint = new Vector3D(hip.getX(), hip.getY(), hip.getZ());
        Vector3D kneePoint = new Vector3D(knee.getX(), knee.getY(), knee.getZ());
        Vector3D anklePoint = new Vector3D(ankle.getX(), ankle.getY(), ankle.getZ());

        Vector3D femurVector = Vector3D.fromPoints(hipPoint, kneePoint);
        Vector3D tibiaVector = Vector3D.fromPoints(kneePoint, anklePoint);

        double angleRaw = femurVector.angleDegrees(tibiaVector);

        double qAngle = 180.0 - angleRaw;

        qAngle = Math.max(0, Math.min(40, qAngle));

        log.debug("Q angle {}: {:.2f}°", side, qAngle);
        return BigDecimal.valueOf(qAngle).setScale(2, RoundingMode.HALF_UP);
    }

    private ForwardHeadResult calculateForwardHeadPosture(
            PythonResponseDTO.KeypointDTO neck,
            PythonResponseDTO.KeypointDTO leftEar,
            PythonResponseDTO.KeypointDTO rightEar
    ) {
        log.debug("Calculating Forward Head Posture");

        Vector3D neckPoint = new Vector3D(neck.getX(), neck.getY(), neck.getZ());
        Vector3D leftEarPoint = new Vector3D(leftEar.getX(), leftEar.getY(), leftEar.getZ());
        Vector3D rightEarPoint = new Vector3D(rightEar.getX(), rightEar.getY(), rightEar.getZ());

        Vector3D earCenter = new Vector3D(
                (leftEarPoint.getX() + rightEarPoint.getX()) / 2,
                (leftEarPoint.getY() + rightEarPoint.getY()) / 2,
                (leftEarPoint.getZ() + rightEarPoint.getZ()) / 2
        );

        Vector3D headVector = Vector3D.fromPoints(neckPoint, earCenter);

        double fhpAngle = headVector.angleFromVertical();

        double distanceMeters = neckPoint.horizontalDistanceTo(earCenter);
        double distanceCm = distanceMeters * 100;

        log.debug("FHP - Angle: {:.2f}°, Distance: {:.2f} cm", fhpAngle, distanceCm);

        return new ForwardHeadResult(
                BigDecimal.valueOf(fhpAngle).setScale(2, RoundingMode.HALF_UP),
                BigDecimal.valueOf(distanceCm).setScale(2, RoundingMode.HALF_UP)
        );
    }

    private BigDecimal calculateShoulderAsymmetry(
            PythonResponseDTO.KeypointDTO leftShoulder,
            PythonResponseDTO.KeypointDTO rightShoulder
    ) {
        log.debug("Calculating shoulder asymmetry");

        double leftZ = leftShoulder.getZ();
        double rightZ = rightShoulder.getZ();

        double asymmetryMeters = Math.abs(leftZ - rightZ);
        double asymmetryCm = asymmetryMeters * 100;

        log.debug("Shoulder asymmetry: {:.2f} cm", asymmetryCm);
        return BigDecimal.valueOf(asymmetryCm).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateGlobalPostureScore(
            BigDecimal qAngleLeft,
            BigDecimal qAngleRight,
            BigDecimal fhpAngle,
            BigDecimal shoulderAsymmetry,
            User user
    ) {
        double totalScore = 0.0;
        double totalWeight = 0.0;

        int userAge = user.getAge();
        boolean isMale = user.getGender().toString().equals("MALE");

        double ageFactor = userAge > 60 ? 0.85 : 1.0;

        double fhpScore = calculateFHPScore(fhpAngle.doubleValue(), ageFactor);
        totalScore += fhpScore * 3;
        totalWeight += 3;
        log.debug("FHP Score: {:.2f} (weight=3)", fhpScore);

        double qAngleAvg = (qAngleLeft.doubleValue() + qAngleRight.doubleValue()) / 2;
        double qAngleScore = calculateQAngleScore(qAngleAvg, isMale, ageFactor);
        totalScore += qAngleScore * 2;
        totalWeight += 2;
        log.debug("Q Angle Score: {:.2f} (weight=2)", qAngleScore);

        double shoulderScore = calculateShoulderScore(shoulderAsymmetry.doubleValue());
        totalScore += shoulderScore * 1;
        totalWeight += 1;
        log.debug("Shoulder Score: {:.2f} (weight=1)", shoulderScore);

        double gps = (totalScore / totalWeight) * 10; // Scalăm la 0-100
        gps = Math.max(0, Math.min(100, gps));

        log.info("Global Posture Score: {:.2f}%", gps);
        return BigDecimal.valueOf(gps).setScale(2, RoundingMode.HALF_UP);

    }

    private double calculateFHPScore(double fhpAngle, double ageFactor) {
        double normalMax = 5.0 * ageFactor;
        double moderateMax = 15.0 * ageFactor;

        if (fhpAngle <= normalMax) {
            return 0.0;
        } else if (fhpAngle <= moderateMax) {

            return ((fhpAngle - normalMax) / (moderateMax - normalMax)) * 5.0;
        } else {

            double excess = Math.min(fhpAngle - moderateMax, 20.0);
            return 5.0 + (excess / 20.0) * 5.0;
        }
    }

    private double calculateQAngleScore(double qAngle, boolean isMale, double ageFactor) {
        double normalMin = isMale ? 10.0 : 15.0;
        double normalMax = isMale ? 14.0 : 17.0;

        normalMin *= ageFactor;
        normalMax *= ageFactor;

        if (qAngle >= normalMin && qAngle <= normalMax) {
            return 0.0;
        } else if (qAngle < normalMin) {
            double deviation = normalMin - qAngle;
            return Math.min(deviation / 5.0, 1.0) * 3.0;
        } else {
            double deviation = qAngle - normalMax;
            if (deviation > 6.0) {
                return 10.0;
            }
            return (deviation / 6.0) * 10.0;
        }
    }

    private double calculateShoulderScore(double asymmetryCm) {
        if (asymmetryCm <= 1.5) {
            return 0.0;
        } else if (asymmetryCm <= 3.0) {
            return ((asymmetryCm - 1.5) / 1.5) * 5.0;
        } else {
            return 5.0 + Math.min((asymmetryCm - 3.0) / 2.0, 1.0) * 5.0; 
        }
    }

    private RiskLevel determineRiskLevel(BigDecimal gps) {
        double score = gps.doubleValue();

        if (score <= 20.0) {
            return RiskLevel.LOW;
        } else if (score <= 50.0) {
            return RiskLevel.MODERATE;
        } else {
            return RiskLevel.HIGH;
        }
    }

    public List<String> generateRecommendations(BiomechanicsMetrics metrics) {
        List<String> recommendations = new ArrayList<>();
        
        if (metrics.getFhpAngle() != null && metrics.getFhpAngle().doubleValue() > 10.0) {
            recommendations.add("Forward Head Posture detected: Recommended exercise - Chin Tucks " +
                    "(Cervical Retraction) to activate deep flexors. " +
                    "Adjust your monitor to eye level.");
        }
        
        double qAngleAvg = (metrics.getQAngleLeft().doubleValue() +
                metrics.getQAngleRight().doubleValue()) / 2;
        if (qAngleAvg > 17.0) {
            recommendations.add("Increased Q Angle detected: Strengthen quadriceps and glutes. " +
                    "Avoid deep squats and running on hard surfaces.");
        }
        
        if (metrics.getShoulderAsymmetryCm() != null &&
                metrics.getShoulderAsymmetryCm().doubleValue() > 2.0) {
            recommendations.add("Shoulder Asymmetry: Check if you carry your bag on one shoulder. " +
                    "Bilateral stretching exercises recommended.");
        }
        
        if (metrics.getRiskLevel() == RiskLevel.HIGH) {
            recommendations.add("WARNING: High risk score detected. " +
                    "Consultation with a physiotherapist or rehabilitation doctor is recommended.");
        }

        return recommendations;
    }

    @lombok.Data
    @lombok.AllArgsConstructor
    private static class ForwardHeadResult {
        BigDecimal angle;
        BigDecimal distanceCm;
    }
}
