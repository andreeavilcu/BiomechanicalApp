package com.biomechanics.backend.mapper;

import com.biomechanics.backend.model.dto.RecommendationDTO;
import com.biomechanics.backend.model.entity.Recommendation;
import org.springframework.stereotype.Component;

@Component
public class RecommendationMapper {
    public RecommendationDTO toDTO(Recommendation entity) {
        if (entity == null) {
            return null;
        }

        return RecommendationDTO.builder()
                .id(entity.getId())
                .metricType(entity.getMetricType())
                .severity(entity.getSeverity())
                .title(entity.getTitle())
                .biomechanicalCause(entity.getBiomechanicalCause())
                .exercise(entity.getExercise())
                .ergonomicTip(entity.getErgonomicTip())
                .isBlocked(entity.getIsBlocked())
                .disclaimerRequired(entity.getDisclaimerRequired())
                .detectedValue(entity.getDetectedValue())
                .normalRange(entity.getNormalRange())
                .build();
    }
}
