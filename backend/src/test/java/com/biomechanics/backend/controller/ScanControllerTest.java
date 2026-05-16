package com.biomechanics.backend.controller;

import com.biomechanics.backend.model.dto.AnalysisResultDTO;
import com.biomechanics.backend.model.dto.CohortBenchmarkDTO;
import com.biomechanics.backend.model.enums.ProcessingStatus;
import com.biomechanics.backend.service.CohortBenchmarkService;
import com.biomechanics.backend.service.ScanSessionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ScanController Tests")
class ScanControllerTest {

    @Mock private ScanSessionService scanSessionService;
    @Mock private CohortBenchmarkService cohortBenchmarkService;

    @InjectMocks
    private ScanController scanController;

    private AnalysisResultDTO sampleResult() {
        return AnalysisResultDTO.builder()
                .sessionId(1L)
                .status(ProcessingStatus.COMPLETED)
                .recommendations(List.of())
                .build();
    }

    private UserDetails mockUserDetails(String username) {
        UserDetails ud = mock(UserDetails.class);
        when(ud.getUsername()).thenReturn(username);
        return ud;
    }

    private UserDetails mockPatientUserDetails(String username) {
        UserDetails ud = mock(UserDetails.class);
        when(ud.getUsername()).thenReturn(username);
        Collection<GrantedAuthority> authorities = List.of((GrantedAuthority) () -> "ROLE_PATIENT");
        doReturn(authorities).when(ud).getAuthorities();
        return ud;
    }

    @Nested
    @DisplayName("uploadScan()")
    class UploadScan {

        @Test
        @DisplayName("200 OK when PATIENT uploads their own scan (isOwner = true)")
        void shouldReturn200ForPatientUploadingOwnScan() {
            UserDetails ud = mockPatientUserDetails("pac@test.com");
            when(scanSessionService.isOwner("pac@test.com", 1L)).thenReturn(true);
            when(scanSessionService.processScan(any(), eq(1L), eq(175.0), eq("LIDAR")))
                    .thenReturn(sampleResult());

            MockMultipartFile file = new MockMultipartFile("file", "scan.ply", null, new byte[]{1, 2, 3});

            ResponseEntity<AnalysisResultDTO> response = scanController.uploadScan(
                    file, 1L, 175.0, "LIDAR", ud);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().getSessionId()).isEqualTo(1L);
        }

        @Test
        @DisplayName("AccessDeniedException when PATIENT uploads scan for another user")
        void shouldThrowForPatientUploadingOtherScan() {
            UserDetails ud = mockPatientUserDetails("pac@test.com");
            when(scanSessionService.isOwner("pac@test.com", 2L)).thenReturn(false);

            MockMultipartFile file = new MockMultipartFile("file", "scan.ply", null, new byte[]{1});

            assertThatThrownBy(() -> scanController.uploadScan(file, 2L, 175.0, "LIDAR", ud))
                    .isInstanceOf(AccessDeniedException.class);

            verify(scanSessionService, never()).processScan(any(), any(), any(), any());
        }
    }

    @Nested
    @DisplayName("getMyHistory()")
    class GetMyHistory {

        @Test
        @DisplayName("200 OK and list of current user's sessions")
        void shouldReturn200WithHistory() {
            UserDetails ud = mockUserDetails("pac@test.com");
            when(scanSessionService.getHistoryByEmail("pac@test.com"))
                    .thenReturn(List.of(sampleResult()));

            ResponseEntity<List<AnalysisResultDTO>> response = scanController.getMyHistory(ud);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).hasSize(1);
        }

        @Test
        @DisplayName("Empty list when no sessions exist")
        void shouldReturn200WithEmptyList() {
            UserDetails ud = mockUserDetails("pac@test.com");
            when(scanSessionService.getHistoryByEmail("pac@test.com")).thenReturn(List.of());

            ResponseEntity<List<AnalysisResultDTO>> response = scanController.getMyHistory(ud);

            assertThat(response.getBody()).isEmpty();
        }
    }

    @Nested
    @DisplayName("getSession()")
    class GetSession {

        @Test
        @DisplayName("200 OK and found session")
        void shouldReturn200WithSession() {
            UserDetails ud = mockUserDetails("pac@test.com");
            when(scanSessionService.getSessionForUser(1L, "pac@test.com")).thenReturn(sampleResult());

            ResponseEntity<AnalysisResultDTO> response = scanController.getSession(1L, ud);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().getSessionId()).isEqualTo(1L);
        }

        @Test
        @DisplayName("AccessDeniedException from service is propagated")
        void shouldPropagateAccessDenied() {
            UserDetails ud = mockUserDetails("pac@test.com");
            when(scanSessionService.getSessionForUser(anyLong(), any()))
                    .thenThrow(new AccessDeniedException("Access denied"));

            assertThatThrownBy(() -> scanController.getSession(1L, ud))
                    .isInstanceOf(AccessDeniedException.class);
        }
    }

    @Nested
    @DisplayName("getUserHistory()")
    class GetUserHistory {

        @Test
        @DisplayName("200 OK and requested user's history")
        void shouldReturn200WithUserHistory() {
            UserDetails ud = mockUserDetails("spec@test.com");
            when(scanSessionService.getHistoryByUserId(1L, "spec@test.com"))
                    .thenReturn(List.of(sampleResult()));

            ResponseEntity<List<AnalysisResultDTO>> response = scanController.getUserHistory(1L, ud);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("deleteSession()")
    class DeleteSession {

        @Test
        @DisplayName("204 No Content on successful deletion")
        void shouldReturn204OnDelete() {
            UserDetails ud = mockUserDetails("pac@test.com");
            doNothing().when(scanSessionService).deleteSession(1L, "pac@test.com");

            ResponseEntity<Void> response = scanController.deleteSession(1L, ud);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        }

        @Test
        @DisplayName("AccessDeniedException is propagated when user has no rights")
        void shouldPropagateAccessDenied() {
            UserDetails ud = mockUserDetails("pac@test.com");
            doThrow(new AccessDeniedException("Access denied"))
                    .when(scanSessionService).deleteSession(anyLong(), any());

            assertThatThrownBy(() -> scanController.deleteSession(1L, ud))
                    .isInstanceOf(AccessDeniedException.class);
        }
    }

    @Nested
    @DisplayName("getPointCloud()")
    class GetPointCloud {

        @Test
        @DisplayName("200 OK with PLY data and correct headers")
        void shouldReturn200WithPlyData() {
            UserDetails ud = mockUserDetails("pac@test.com");
            byte[] plyData = new byte[]{0x70, 0x6c, 0x79};
            when(scanSessionService.getPointCloud(1L, "pac@test.com")).thenReturn(plyData);

            ResponseEntity<byte[]> response = scanController.getPointCloud(1L, ud);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isEqualTo(plyData);
            assertThat(response.getHeaders().getFirst("Content-Type"))
                    .isEqualTo("application/octet-stream");
        }

        @Test
        @DisplayName("404 Not Found when point cloud does not exist")
        void shouldReturn404WhenNoPointCloud() {
            UserDetails ud = mockUserDetails("pac@test.com");
            when(scanSessionService.getPointCloud(1L, "pac@test.com")).thenReturn(null);

            ResponseEntity<byte[]> response = scanController.getPointCloud(1L, ud);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("getCohortBenchmark()")
    class GetCohortBenchmark {

        @Test
        @DisplayName("200 OK with benchmark data")
        void shouldReturn200WithBenchmark() {
            CohortBenchmarkDTO benchmark = CohortBenchmarkDTO.builder()
                    .totalSessions(50L)
                    .build();
            when(cohortBenchmarkService.getBenchmark()).thenReturn(benchmark);

            ResponseEntity<CohortBenchmarkDTO> response = scanController.getCohortBenchmark();

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().getTotalSessions()).isEqualTo(50L);
        }
    }
}
