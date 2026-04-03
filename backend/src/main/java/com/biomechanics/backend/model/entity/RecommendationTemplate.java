package com.biomechanics.backend.model.entity;

import com.biomechanics.backend.model.enums.MetricType;
import com.biomechanics.backend.model.enums.RecommendationSeverity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;


@Entity
@Table(name = "recommendation_templates",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_metric_severity",
                columnNames = {"metric_type", "severity"}
        ))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecommendationTemplate {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "metric_type", length = 30, nullable = false)
    private MetricType metricType;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", length = 20, nullable = false)
    private RecommendationSeverity severity;

    @Column(name = "title", length = 200, nullable = false)
    private String title;

    @Column(name = "biomechanical_cause", columnDefinition = "TEXT", nullable = false)
    private String biomechanicalCause;

    @Column(name = "exercise", columnDefinition = "TEXT")
    private String exercise;

    @Column(name = "ergonomic_tip", columnDefinition = "TEXT")
    private String ergonomicTip;

    @Column(name = "blocked_ergonomic_tip", columnDefinition = "TEXT")
    private String blockedErgonomicTip;

    @Column(name = "normal_range_male", length = 50)
    private String normalRangeMale;

    @Column(name = "normal_range_female", length = 50)
    private String normalRangeFemale;
}
