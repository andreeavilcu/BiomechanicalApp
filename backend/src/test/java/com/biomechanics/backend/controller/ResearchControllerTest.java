package com.biomechanics.backend.controller;

import com.biomechanics.backend.model.dto.research.AggregateMetricsDTO;
import com.biomechanics.backend.model.dto.research.PostureTrendDTO;
import com.biomechanics.backend.service.ResearchService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ResearchController Tests")
class ResearchControllerTest {

    @Mock private ResearchService researchService;

    @InjectMocks
    private ResearchController researchController;

    @Nested
    @DisplayName("getAggregateMetrics()")
    class GetAggregateMetrics {

        @Test
        @DisplayName("200 OK and aggregate metrics without date filters")
        void shouldReturn200WithMetrics() {
            AggregateMetricsDTO dto = new AggregateMetricsDTO();
            when(researchService.getAggregateMetrics(null, null)).thenReturn(dto);

            ResponseEntity<AggregateMetricsDTO> response =
                    researchController.getAggregateMetrics(null, null);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
        }

        @Test
        @DisplayName("Service receives correct date filters")
        void shouldPassDateFiltersToService() {
            LocalDate from = LocalDate.of(2024, 1, 1);
            LocalDate to   = LocalDate.of(2024, 12, 31);
            when(researchService.getAggregateMetrics(from, to)).thenReturn(new AggregateMetricsDTO());

            researchController.getAggregateMetrics(from, to);

            verify(researchService).getAggregateMetrics(from, to);
        }

        @Test
        @DisplayName("Exception from service is propagated")
        void shouldPropagateServiceException() {
            when(researchService.getAggregateMetrics(any(), any()))
                    .thenThrow(new RuntimeException("Calculation error"));

            assertThatThrownBy(() -> researchController.getAggregateMetrics(null, null))
                    .isInstanceOf(RuntimeException.class);
        }
    }

    @Nested
    @DisplayName("getPostureTrends()")
    class GetPostureTrends {

        @Test
        @DisplayName("200 OK and list of trends for the last 90 days")
        void shouldReturn200WithTrends() {
            List<PostureTrendDTO> trends = List.of(new PostureTrendDTO(), new PostureTrendDTO());
            when(researchService.getPostureTrends(90)).thenReturn(trends);

            ResponseEntity<List<PostureTrendDTO>> response =
                    researchController.getPostureTrends(90);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).hasSize(2);
        }

        @Test
        @DisplayName("Service receives correct number of days")
        void shouldPassLastDaysToService() {
            when(researchService.getPostureTrends(30)).thenReturn(List.of());

            researchController.getPostureTrends(30);

            verify(researchService).getPostureTrends(30);
        }

        @Test
        @DisplayName("Empty list is returned without errors")
        void shouldHandleEmptyTrends() {
            when(researchService.getPostureTrends(anyInt())).thenReturn(List.of());

            ResponseEntity<List<PostureTrendDTO>> response =
                    researchController.getPostureTrends(90);

            assertThat(response.getBody()).isEmpty();
        }
    }

    @Nested
    @DisplayName("exportAnonymizedCsv()")
    class ExportAnonymizedCsv {

        @Test
        @DisplayName("200 OK with CSV content and correct headers")
        void shouldReturn200WithCsvContent() {
            String csvContent = "id,gps,risk\n1,25.0,LOW\n";
            when(researchService.exportAnonymizedCsv(any(), any())).thenReturn(csvContent);

            ResponseEntity<String> response =
                    researchController.exportAnonymizedCsv(null, null);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).contains("gps");
            assertThat(response.getHeaders().getFirst("Content-Type")).contains("text/csv");
            assertThat(response.getHeaders().getFirst("Content-Disposition"))
                    .contains("attachment");
        }

        @Test
        @DisplayName("Service receives correct date filters")
        void shouldPassDateFiltersToService() {
            LocalDate from = LocalDate.of(2025, 1, 1);
            LocalDate to   = LocalDate.of(2025, 6, 30);
            when(researchService.exportAnonymizedCsv(from, to)).thenReturn("id,gps\n");

            researchController.exportAnonymizedCsv(from, to);

            verify(researchService).exportAnonymizedCsv(from, to);
        }
    }
}
