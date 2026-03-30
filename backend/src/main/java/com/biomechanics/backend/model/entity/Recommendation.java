// model/entity/Recommendation.java
package com.biomechanics.backend.model.entity;

import com.biomechanics.backend.model.enums.MetricType;
import com.biomechanics.backend.model.enums.RecommendationSeverity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "recommendations")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Recommendation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "recommendation_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private ScanSession scanSession;

    @Enumerated(EnumType.STRING)
    @Column(name = "metric_type", length = 30, nullable = false)
    private MetricType metricType;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", length = 20, nullable = false)
    private RecommendationSeverity severity;

    @Column(name = "title", length = 200, nullable = false)
    private String title;

    @Column(name = "biomechanical_cause", columnDefinition = "TEXT")
    private String biomechanicalCause;

    @Column(name = "exercise", columnDefinition = "TEXT")
    private String exercise;

    @Column(name = "ergonomic_tip", columnDefinition = "TEXT")
    private String ergonomicTip;

    @Column(name = "is_blocked", nullable = false)
    private Boolean isBlocked = false;

    @Column(name = "disclaimer_required", nullable = false)
    private Boolean disclaimerRequired = false;

    @Column(name = "detected_value", length = 50)
    private String detectedValue;

    @Column(name = "normal_range", length = 50)
    private String normalRange;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}