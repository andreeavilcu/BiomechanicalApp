package com.biomechanics.backend.repository;

import com.biomechanics.backend.model.entity.RecommendationTemplate;
import com.biomechanics.backend.model.enums.MetricType;
import com.biomechanics.backend.model.enums.RecommendationSeverity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

public interface RecommendationTemplateRepository extends JpaRepository<RecommendationTemplate, Long> {
    Optional<RecommendationTemplate> findByMetricTypeAndSeverity(
            MetricType metricType, RecommendationSeverity severity);
}
