package com.biomechanics.backend.mapper;

import com.biomechanics.backend.model.dto.RecommendationDTO;
import com.biomechanics.backend.model.entity.Recommendation;
import com.biomechanics.backend.model.enums.MetricType;
import com.biomechanics.backend.model.enums.RecommendationSeverity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("RecommendationMapper Tests")
class RecommendationMapperTest {

    @InjectMocks
    private RecommendationMapper recommendationMapper;

    private Recommendation buildRecommendation() {
        return Recommendation.builder()
                .id(1L)
                .metricType(MetricType.FHP)
                .severity(RecommendationSeverity.MODERATE)
                .title("Forward Head Posture")
                .biomechanicalCause("Tensiune cervicala crescuta")
                .exercise("Chin tucks")
                .ergonomicTip("Monitor la nivelul ochilor")
                .isBlocked(false)
                .disclaimerRequired(false)
                .detectedValue("12.5°")
                .normalRange("0° - 5°")
                .build();
    }

    @Nested
    @DisplayName("toDTO()")
    class ToDTO {

        @Test
        @DisplayName("Valid entity - DTO populated with all fields")
        void shouldMapAllFields() {
            Recommendation rec = buildRecommendation();

            RecommendationDTO dto = recommendationMapper.toDTO(rec);

            assertThat(dto).isNotNull();
            assertThat(dto.getId()).isEqualTo(1L);
            assertThat(dto.getMetricType()).isEqualTo(MetricType.FHP);
            assertThat(dto.getSeverity()).isEqualTo(RecommendationSeverity.MODERATE);
            assertThat(dto.getTitle()).isEqualTo("Forward Head Posture");
            assertThat(dto.getBiomechanicalCause()).isEqualTo("Tensiune cervicala crescuta");
            assertThat(dto.getExercise()).isEqualTo("Chin tucks");
            assertThat(dto.getErgonomicTip()).isEqualTo("Monitor la nivelul ochilor");
            assertThat(dto.getIsBlocked()).isFalse();
            assertThat(dto.getDisclaimerRequired()).isFalse();
            assertThat(dto.getDetectedValue()).isEqualTo("12.5°");
            assertThat(dto.getNormalRange()).isEqualTo("0° - 5°");
        }

        @Test
        @DisplayName("Null entity - returns null")
        void shouldReturnNullForNullInput() {
            assertThat(recommendationMapper.toDTO(null)).isNull();
        }

        @Test
        @DisplayName("Null exercise (blocked recommendation) - DTO preserves null")
        void shouldPreserveNullExercise() {
            Recommendation rec = buildRecommendation();
            rec.setExercise(null);
            rec.setIsBlocked(true);
            rec.setDisclaimerRequired(true);

            RecommendationDTO dto = recommendationMapper.toDTO(rec);

            assertThat(dto.getExercise()).isNull();
            assertThat(dto.getIsBlocked()).isTrue();
            assertThat(dto.getDisclaimerRequired()).isTrue();
        }

        @Test
        @DisplayName("GLOBAL type - metricType mapped correctly")
        void shouldMapGlobalMetricType() {
            Recommendation rec = buildRecommendation();
            rec.setMetricType(MetricType.GLOBAL);
            rec.setSeverity(RecommendationSeverity.HIGH);

            RecommendationDTO dto = recommendationMapper.toDTO(rec);

            assertThat(dto.getMetricType()).isEqualTo(MetricType.GLOBAL);
            assertThat(dto.getSeverity()).isEqualTo(RecommendationSeverity.HIGH);
        }
    }
}
