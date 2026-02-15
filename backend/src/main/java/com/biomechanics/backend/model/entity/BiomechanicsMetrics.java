package com.biomechanics.backend.model.entity;

import com.biomechanics.backend.model.enums.RiskLevel;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "biomechanics_metrics")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BiomechanicsMetrics {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "metric_id")
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private ScanSession scanSession;

    @Column(name = "q_angle_left", precision = 6, scale = 2)
    private BigDecimal qAngleLeft;

    @Column(name = "q_angle_right", precision = 6, scale = 2)
    private BigDecimal qAngleRight;

    @Column(name = "fhp_angle", precision = 6, scale = 2)
    private BigDecimal fhpAngle;

    @Column(name = "fhp_distance_cm", precision = 6, scale = 2)
    private BigDecimal fhpDistanceCm;

    @Column(name = "shoulder_asymmetry_cm", precision = 6, scale = 2)
    private BigDecimal shoulderAsymmetryCm;


    @Column(name = "stance_phase_left", precision = 5, scale = 2)
    private BigDecimal stancePhaseLeft;

    @Column(name = "stance_phase_right", precision = 5, scale = 2)
    private BigDecimal stancePhaseRight;

    @Column(name = "swing_phase_left", precision = 5, scale = 2)
    private BigDecimal swingPhaseLeft;

    @Column(name = "swing_phase_right", precision = 5, scale = 2)
    private BigDecimal swingPhaseRight;

    @Column(name = "knee_flexion_max_left", precision = 6, scale = 2)
    private BigDecimal kneeFlexionMaxLeft;

    @Column(name = "knee_flexion_max_right", precision = 6, scale = 2)
    private BigDecimal kneeFlexionMaxRight;

    @Column(name = "cadence")
    private Integer cadence;


    @Column(name = "global_posture_score", precision = 5, scale = 2)
    private BigDecimal globalPostureScore;

    @Enumerated(EnumType.STRING)
    @Column(name = "risk_level", length = 20)
    private RiskLevel riskLevel;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
