package com.biomechanics.backend.service;

import com.biomechanics.backend.mapper.RecommendationMapper;
import com.biomechanics.backend.mapper.UserMapper;
import com.biomechanics.backend.model.dto.AnalysisResultDTO;
import com.biomechanics.backend.model.dto.PythonResponseDTO;
import com.biomechanics.backend.model.entity.BiomechanicsMetrics;
import com.biomechanics.backend.model.entity.RawKeypoints;
import com.biomechanics.backend.model.entity.ScanSession;
import com.biomechanics.backend.model.entity.User;
import com.biomechanics.backend.model.enums.ProcessingStatus;
import com.biomechanics.backend.model.enums.RiskLevel;
import com.biomechanics.backend.model.enums.UserRole;
import com.biomechanics.backend.repository.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ScanSessionService Tests")
class ScanSessionServiceTest {

    @Mock private ScanSessionRepository scanSessionRepository;
    @Mock private BiomechanicsMetricsRepository  metricsRepository;
    @Mock private RawKeypointsRepository rawKeypointsRepository;
    @Mock private RecommendationRepository recommendationRepository;
    @Mock private UserRepository userRepository;
    @Mock private PythonIntegrationService pythonIntegrationService;
    @Mock private BiomechanicsService biomechanicsService;
    @Mock private RecommendationService recommendationService;
    @Mock private UserMapper userMapper;
    @Mock private RecommendationMapper  recommendationMapper;

    @InjectMocks
    private ScanSessionService scanSessionService;

    private User buildUser(Long id, String email, UserRole role) {
        User u = new User();
        u.setId(id);
        u.setEmail(email);
        u.setRole(role);
        u.setIsActive(true);
        u.setDateOfBirth(LocalDate.of(1990, 1, 1));
        return u;
    }

    private ScanSession buildSession(Long id, User user, ProcessingStatus status) {
        ScanSession s = new ScanSession();
        s.setId(id);
        s.setUser(user);
        s.setProcessingStatus(status);
        s.setScanDate(LocalDateTime.now());
        s.setScanFilePath("scan.ply");
        return s;
    }

    private BiomechanicsMetrics buildMetrics(ScanSession session) {
        BiomechanicsMetrics m = new BiomechanicsMetrics();
        m.setId(1L);
        m.setScanSession(session);
        m.setGlobalPostureScore(BigDecimal.valueOf(25));
        m.setRiskLevel(RiskLevel.MODERATE);
        m.setQAngleLeft(BigDecimal.valueOf(12));
        m.setQAngleRight(BigDecimal.valueOf(13));
        m.setFhpAngle(BigDecimal.valueOf(8));
        m.setFhpDistanceCm(BigDecimal.valueOf(3));
        m.setShoulderAsymmetryCm(BigDecimal.valueOf(1));
        return m;
    }

    @Nested
    @DisplayName("validateFile() via processScan()")
    class ValidateFile {

        @Test
        @DisplayName("Empty file throws IllegalArgumentException")
        void shouldThrowForEmptyFile() {
            User user = buildUser(1L, "pac@test.com", UserRole.PATIENT);
            when(userRepository.findById(1L)).thenReturn(Optional.of(user));

            MultipartFile empty = new MockMultipartFile("file", "scan.ply", null, new byte[0]);

            assertThatThrownBy(() -> scanSessionService.processScan(empty, 1L, 175.0, "LIDAR"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("empty");
        }

        @Test
        @DisplayName("Invalid extension throws IllegalArgumentException")
        void shouldThrowForInvalidExtension() {
            User user = buildUser(1L, "pac@test.com", UserRole.PATIENT);
            when(userRepository.findById(1L)).thenReturn(Optional.of(user));

            MultipartFile txt = new MockMultipartFile("file", "scan.txt", null, new byte[]{1, 2, 3});

            assertThatThrownBy(() -> scanSessionService.processScan(txt, 1L, 175.0, "LIDAR"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Invalid file format");
        }

        @Test
        @DisplayName("Non-existent user throws RuntimeException before file validation")
        void shouldThrowWhenUserNotFound() {
            when(userRepository.findById(999L)).thenReturn(Optional.empty());

            MultipartFile f = new MockMultipartFile("file", "scan.ply", null, new byte[]{1, 2, 3});

            assertThatThrownBy(() -> scanSessionService.processScan(f, 999L, 175.0, "LIDAR"))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("999");
        }
    }

    @Nested
    @DisplayName("getHistoryByEmail()")
    class GetHistoryByEmail {

        @Test
        @DisplayName("Returns user's session list in descending order")
        void shouldReturnSessionList() {
            User user = buildUser(1L, "pac@test.com", UserRole.PATIENT);
            ScanSession s1 = buildSession(1L, user, ProcessingStatus.COMPLETED);
            ScanSession s2 = buildSession(2L, user, ProcessingStatus.COMPLETED);

            when(userMapper.getUserByEmail("pac@test.com")).thenReturn(user);
            when(scanSessionRepository.findByUserOrderByScanDateDesc(user))
                    .thenReturn(List.of(s1, s2));

            when(metricsRepository.findByScanSession(any())).thenReturn(Optional.empty());
            when(rawKeypointsRepository.findByScanSession(any())).thenReturn(Optional.empty());

            List<AnalysisResultDTO> result = scanSessionService.getHistoryByEmail("pac@test.com");

            assertThat(result).hasSize(2);
        }

        @Test
        @DisplayName("User with no sessions returns empty list")
        void shouldReturnEmptyListWhenNoSessions() {
            User user = buildUser(1L, "pac@test.com", UserRole.PATIENT);

            when(userMapper.getUserByEmail("pac@test.com")).thenReturn(user);
            when(scanSessionRepository.findByUserOrderByScanDateDesc(user)).thenReturn(List.of());

            assertThat(scanSessionService.getHistoryByEmail("pac@test.com")).isEmpty();
        }
    }

    @Nested
    @DisplayName("getSessionForUser()")
    class GetSessionForUser {

        @Test
        @DisplayName("PATIENT accesses their own session - OK")
        void patientCanAccessOwnSession() {
            User patient = buildUser(1L, "pac@test.com", UserRole.PATIENT);
            ScanSession session = buildSession(10L, patient, ProcessingStatus.COMPLETED);

            when(userMapper.getUserByEmail("pac@test.com")).thenReturn(patient);
            when(scanSessionRepository.findById(10L)).thenReturn(Optional.of(session));
            when(metricsRepository.findByScanSession(session)).thenReturn(Optional.empty());
            when(rawKeypointsRepository.findByScanSession(session)).thenReturn(Optional.empty());

            AnalysisResultDTO result = scanSessionService.getSessionForUser(10L, "pac@test.com");

            assertThat(result).isNotNull();
            assertThat(result.getSessionId()).isEqualTo(10L);
        }

        @Test
        @DisplayName("PATIENT accesses someone else's session - AccessDeniedException")
        void patientCannotAccessOtherSession() {
            User patient = buildUser(1L, "pac@test.com", UserRole.PATIENT);
            User other   = buildUser(2L, "altul@test.com", UserRole.PATIENT);
            ScanSession session = buildSession(10L, other, ProcessingStatus.COMPLETED);

            when(userMapper.getUserByEmail("pac@test.com")).thenReturn(patient);
            when(scanSessionRepository.findById(10L)).thenReturn(Optional.of(session));

            assertThatThrownBy(() -> scanSessionService.getSessionForUser(10L, "pac@test.com"))
                    .isInstanceOf(AccessDeniedException.class);
        }

        @Test
        @DisplayName("ADMIN accesses any session - OK")
        void adminCanAccessAnySession() {
            User admin  = buildUser(9L, "admin@test.com", UserRole.ADMIN);
            User owner  = buildUser(1L, "pac@test.com",   UserRole.PATIENT);
            ScanSession session = buildSession(10L, owner, ProcessingStatus.COMPLETED);

            when(userMapper.getUserByEmail("admin@test.com")).thenReturn(admin);
            when(scanSessionRepository.findById(10L)).thenReturn(Optional.of(session));
            when(metricsRepository.findByScanSession(session)).thenReturn(Optional.empty());
            when(rawKeypointsRepository.findByScanSession(session)).thenReturn(Optional.empty());

            assertThatNoException().isThrownBy(() ->
                    scanSessionService.getSessionForUser(10L, "admin@test.com"));
        }

        @Test
        @DisplayName("RESEARCHER cannot access individual sessions - AccessDeniedException")
        void researcherCannotAccessSessions() {
            User researcher = buildUser(5L, "res@test.com", UserRole.RESEARCHER);
            ScanSession session = buildSession(10L, buildUser(1L, "pac@test.com", UserRole.PATIENT),
                    ProcessingStatus.COMPLETED);

            when(userMapper.getUserByEmail("res@test.com")).thenReturn(researcher);
            when(scanSessionRepository.findById(10L)).thenReturn(Optional.of(session));

            assertThatThrownBy(() -> scanSessionService.getSessionForUser(10L, "res@test.com"))
                    .isInstanceOf(AccessDeniedException.class);
        }

        @Test
        @DisplayName("Non-existent session throws RuntimeException")
        void shouldThrowForNonExistentSession() {
            User patient = buildUser(1L, "pac@test.com", UserRole.PATIENT);
            when(userMapper.getUserByEmail("pac@test.com")).thenReturn(patient);
            when(scanSessionRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> scanSessionService.getSessionForUser(999L, "pac@test.com"))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("999");
        }

        @Test
        @DisplayName("SPECIALIST accesses assigned patient's session - OK")
        void specialistCanAccessAssignedPatientSession() {
            User specialist = buildUser(2L, "spec@test.com", UserRole.SPECIALIST);
            User patient    = buildUser(1L, "pac@test.com",  UserRole.PATIENT);
            ScanSession session = buildSession(10L, patient, ProcessingStatus.COMPLETED);

            when(userMapper.getUserByEmail("spec@test.com")).thenReturn(specialist);
            when(scanSessionRepository.findById(10L)).thenReturn(Optional.of(session));
            when(scanSessionRepository.existsByUserAndSpecialist(patient, specialist)).thenReturn(true);
            when(metricsRepository.findByScanSession(session)).thenReturn(Optional.empty());
            when(rawKeypointsRepository.findByScanSession(session)).thenReturn(Optional.empty());

            assertThatNoException().isThrownBy(() ->
                    scanSessionService.getSessionForUser(10L, "spec@test.com"));
        }

        @Test
        @DisplayName("SPECIALIST accesses unassigned patient's session - AccessDeniedException")
        void specialistCannotAccessUnassignedPatientSession() {
            User specialist = buildUser(2L, "spec@test.com", UserRole.SPECIALIST);
            User patient    = buildUser(1L, "pac@test.com",  UserRole.PATIENT);
            ScanSession session = buildSession(10L, patient, ProcessingStatus.COMPLETED);

            when(userMapper.getUserByEmail("spec@test.com")).thenReturn(specialist);
            when(scanSessionRepository.findById(10L)).thenReturn(Optional.of(session));
            when(scanSessionRepository.existsByUserAndSpecialist(patient, specialist)).thenReturn(false);

            assertThatThrownBy(() -> scanSessionService.getSessionForUser(10L, "spec@test.com"))
                    .isInstanceOf(AccessDeniedException.class);
        }
    }

    @Nested
    @DisplayName("getHistoryByUserId()")
    class GetHistoryByUserId {

        @Test
        @DisplayName("SPECIALIST with unassigned patient - AccessDeniedException")
        void specialistCannotAccessUnassignedPatient() {
            User specialist = buildUser(2L, "spec@test.com", UserRole.SPECIALIST);
            User patient    = buildUser(1L, "pac@test.com",  UserRole.PATIENT);

            when(userMapper.getUserByEmail("spec@test.com")).thenReturn(specialist);
            when(userRepository.findById(1L)).thenReturn(Optional.of(patient));
            when(scanSessionRepository.existsByUserAndSpecialist(patient, specialist)).thenReturn(false);

            assertThatThrownBy(() ->
                    scanSessionService.getHistoryByUserId(1L, "spec@test.com"))
                    .isInstanceOf(AccessDeniedException.class);
        }

        @Test
        @DisplayName("SPECIALIST with assigned patient - returns history")
        void specialistCanAccessAssignedPatient() {
            User specialist = buildUser(2L, "spec@test.com", UserRole.SPECIALIST);
            User patient    = buildUser(1L, "pac@test.com",  UserRole.PATIENT);
            ScanSession session = buildSession(5L, patient, ProcessingStatus.COMPLETED);

            when(userMapper.getUserByEmail("spec@test.com")).thenReturn(specialist);
            when(userRepository.findById(1L)).thenReturn(Optional.of(patient));
            when(scanSessionRepository.existsByUserAndSpecialist(patient, specialist)).thenReturn(true);
            when(scanSessionRepository.findByUserOrderByScanDateDesc(patient)).thenReturn(List.of(session));
            when(metricsRepository.findByScanSession(session)).thenReturn(Optional.empty());
            when(rawKeypointsRepository.findByScanSession(session)).thenReturn(Optional.empty());

            List<AnalysisResultDTO> result = scanSessionService.getHistoryByUserId(1L, "spec@test.com");

            assertThat(result).hasSize(1);
        }
    }

    @Nested
    @DisplayName("deleteSession()")
    class DeleteSession {

        @Test
        @DisplayName("Owner can delete their own session")
        void ownerCanDeleteSession() {
            User patient = buildUser(1L, "pac@test.com", UserRole.PATIENT);
            ScanSession session = buildSession(10L, patient, ProcessingStatus.COMPLETED);

            when(userMapper.getUserByEmail("pac@test.com")).thenReturn(patient);
            when(scanSessionRepository.findById(10L)).thenReturn(Optional.of(session));

            scanSessionService.deleteSession(10L, "pac@test.com");

            verify(scanSessionRepository).delete(session);
            verify(metricsRepository).deleteByScanSession(session);
            verify(recommendationRepository).deleteByScanSession(session);
            verify(rawKeypointsRepository).deleteByScanSession(session);
        }

        @Test
        @DisplayName("Admin can delete any session")
        void adminCanDeleteAnySession() {
            User admin  = buildUser(9L, "admin@test.com", UserRole.ADMIN);
            User owner  = buildUser(1L, "pac@test.com",   UserRole.PATIENT);
            ScanSession session = buildSession(10L, owner, ProcessingStatus.COMPLETED);

            when(userMapper.getUserByEmail("admin@test.com")).thenReturn(admin);
            when(scanSessionRepository.findById(10L)).thenReturn(Optional.of(session));

            scanSessionService.deleteSession(10L, "admin@test.com");

            verify(scanSessionRepository).delete(session);
        }

        @Test
        @DisplayName("Non-owner without ADMIN role - AccessDeniedException")
        void nonOwnerNonAdminCannotDelete() {
            User intrus = buildUser(5L, "intrus@test.com", UserRole.PATIENT);
            User owner  = buildUser(1L, "pac@test.com",    UserRole.PATIENT);
            ScanSession session = buildSession(10L, owner, ProcessingStatus.COMPLETED);

            when(userMapper.getUserByEmail("intrus@test.com")).thenReturn(intrus);
            when(scanSessionRepository.findById(10L)).thenReturn(Optional.of(session));

            assertThatThrownBy(() -> scanSessionService.deleteSession(10L, "intrus@test.com"))
                    .isInstanceOf(AccessDeniedException.class);

            verify(scanSessionRepository, never()).delete(any());
        }

        @Test
        @DisplayName("Non-existent session throws RuntimeException")
        void shouldThrowForNonExistentSession() {
            User patient = buildUser(1L, "pac@test.com", UserRole.PATIENT);
            when(userMapper.getUserByEmail("pac@test.com")).thenReturn(patient);
            when(scanSessionRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> scanSessionService.deleteSession(999L, "pac@test.com"))
                    .isInstanceOf(RuntimeException.class);
        }
    }

    @Nested
    @DisplayName("isOwner()")
    class IsOwner {

        @Test
        @DisplayName("Returns true when email belongs to the user with the given ID")
        void shouldReturnTrueForOwner() {
            User user = buildUser(1L, "pac@test.com", UserRole.PATIENT);
            when(userRepository.findByEmail("pac@test.com")).thenReturn(Optional.of(user));

            assertThat(scanSessionService.isOwner("pac@test.com", 1L)).isTrue();
        }

        @Test
        @DisplayName("Returns false when ID does not match")
        void shouldReturnFalseForNonOwner() {
            User user = buildUser(2L, "pac@test.com", UserRole.PATIENT);
            when(userRepository.findByEmail("pac@test.com")).thenReturn(Optional.of(user));

            assertThat(scanSessionService.isOwner("pac@test.com", 1L)).isFalse();
        }

        @Test
        @DisplayName("Returns false when user not found")
        void shouldReturnFalseWhenUserNotFound() {
            when(userRepository.findByEmail("ghost@test.com")).thenReturn(Optional.empty());

            assertThat(scanSessionService.isOwner("ghost@test.com", 1L)).isFalse();
        }
    }

    @Nested
    @DisplayName("getPointCloud()")
    class GetPointCloud {

        @Test
        @DisplayName("Null point cloud returns null")
        void shouldReturnNullWhenNoPointCloud() {
            User owner = buildUser(1L, "pac@test.com", UserRole.PATIENT);
            ScanSession session = buildSession(10L, owner, ProcessingStatus.COMPLETED);
            session.setPointCloudData(null);

            when(scanSessionRepository.findById(10L)).thenReturn(Optional.of(session));
            when(userRepository.findByEmail("pac@test.com")).thenReturn(Optional.of(owner));

            assertThat(scanSessionService.getPointCloud(10L, "pac@test.com")).isNull();
        }

        @Test
        @DisplayName("Non-owner without privileged role - AccessDeniedException")
        void shouldThrowForUnprivilegedNonOwner() {
            User owner   = buildUser(1L, "pac@test.com",   UserRole.PATIENT);
            User intrus  = buildUser(2L, "intrus@test.com", UserRole.PATIENT);
            ScanSession session = buildSession(10L, owner, ProcessingStatus.COMPLETED);
            session.setPointCloudData(new byte[]{1, 2, 3});

            when(scanSessionRepository.findById(10L)).thenReturn(Optional.of(session));
            when(userRepository.findByEmail("intrus@test.com")).thenReturn(Optional.of(intrus));

            assertThatThrownBy(() -> scanSessionService.getPointCloud(10L, "intrus@test.com"))
                    .isInstanceOf(AccessDeniedException.class);
        }

        @Test
        @DisplayName("Non-existent session throws RuntimeException")
        void shouldThrowForNonExistentSession() {
            when(scanSessionRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> scanSessionService.getPointCloud(999L, "pac@test.com"))
                    .isInstanceOf(RuntimeException.class);
        }
    }

    // ─────────────────────────────────────────────────────────────
    // processScan()
    // ─────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("processScan()")
    class ProcessScan {

        @Test
        @DisplayName("Happy path - sesiune procesata cu succes si status COMPLETED")
        void shouldProcessScanSuccessfully() throws Exception {
            User user = buildUser(1L, "pac@test.com", UserRole.PATIENT);
            ScanSession savedSession = buildSession(10L, user, ProcessingStatus.COMPLETED);

            PythonResponseDTO pythonResponse = new PythonResponseDTO();
            PythonResponseDTO.MetadataDTO meta = new PythonResponseDTO.MetadataDTO();
            meta.setMethod("AI_LIDAR");
            meta.setBestScore(0.95);
            meta.setScalingFactor(1.0);
            pythonResponse.setMeta(meta);

            PythonResponseDTO.KeypointDTO nose = new PythonResponseDTO.KeypointDTO();
            nose.setX(0.0); nose.setY(1.0); nose.setZ(0.0);
            pythonResponse.setNose(nose);

            BiomechanicsMetrics metrics = buildMetrics(savedSession);

            when(userRepository.findById(1L)).thenReturn(Optional.of(user));
            when(scanSessionRepository.save(any())).thenReturn(savedSession);
            when(pythonIntegrationService.processScanFile(any(), any())).thenReturn(pythonResponse);
            when(biomechanicsService.calculateMetrics(any(), any())).thenReturn(metrics);
            when(recommendationService.generateAndSave(any(), any(), any())).thenReturn(List.of());
            when(metricsRepository.save(any())).thenReturn(metrics);
            when(rawKeypointsRepository.save(any())).thenReturn(new RawKeypoints());
            when(recommendationRepository.findByScanSessionOrderBySeverityDesc(any())).thenReturn(List.of());
            when(rawKeypointsRepository.findByScanSession(any())).thenReturn(Optional.empty());
            when(scanSessionRepository.findByUserOrderByScanDateDesc(user)).thenReturn(List.of(savedSession));

            MockMultipartFile file = new MockMultipartFile("file", "scan.ply", null, new byte[]{1, 2, 3});
            AnalysisResultDTO result = scanSessionService.processScan(file, 1L, 175.0, "LIDAR");

            assertThat(result).isNotNull();
            assertThat(result.getSessionId()).isEqualTo(10L);
            verify(rawKeypointsRepository).save(any());
            verify(metricsRepository).save(any());
        }

        @Test
        @DisplayName("Eroare Python - status FAILED si RuntimeException aruncata")
        void shouldSetFailedStatusOnProcessingException() throws Exception {
            User user = buildUser(1L, "pac@test.com", UserRole.PATIENT);
            ScanSession savedSession = buildSession(10L, user, ProcessingStatus.PENDING);

            when(userRepository.findById(1L)).thenReturn(Optional.of(user));
            when(scanSessionRepository.save(any())).thenReturn(savedSession);
            when(pythonIntegrationService.processScanFile(any(), any()))
                    .thenThrow(new RuntimeException("Python service unreachable"));

            MockMultipartFile file = new MockMultipartFile("file", "scan.ply", null, new byte[]{1, 2, 3});

            assertThatThrownBy(() -> scanSessionService.processScan(file, 1L, 175.0, "LIDAR"))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Scan processing failed");

            verify(scanSessionRepository, atLeast(2)).save(any());
        }
    }

    @Nested
    @DisplayName("calculateEvolution() indirectly via getSessionForUser()")
    class CalculateEvolution {

        private BiomechanicsMetrics buildFullMetrics(ScanSession session, double gps) {
            BiomechanicsMetrics m = new BiomechanicsMetrics();
            m.setScanSession(session);
            m.setGlobalPostureScore(BigDecimal.valueOf(gps));
            m.setRiskLevel(RiskLevel.MODERATE);
            m.setQAngleLeft(BigDecimal.valueOf(12));
            m.setQAngleRight(BigDecimal.valueOf(13));
            m.setFhpAngle(BigDecimal.valueOf(8));
            m.setFhpDistanceCm(BigDecimal.valueOf(3));
            m.setShoulderAsymmetryCm(BigDecimal.valueOf(1));
            return m;
        }

        @Test
        @DisplayName("FIRST_SESSION - no previous COMPLETED session")
        void shouldReturnFirstSessionTrend() {
            User user = buildUser(1L, "pac@test.com", UserRole.PATIENT);
            ScanSession currentSession = buildSession(10L, user, ProcessingStatus.COMPLETED);
            BiomechanicsMetrics metrics = buildFullMetrics(currentSession, 25.0);

            when(userMapper.getUserByEmail("pac@test.com")).thenReturn(user);
            when(scanSessionRepository.findById(10L)).thenReturn(Optional.of(currentSession));
            when(metricsRepository.findByScanSession(currentSession)).thenReturn(Optional.of(metrics));
            when(recommendationRepository.findByScanSessionOrderBySeverityDesc(currentSession))
                    .thenReturn(List.of());
            when(rawKeypointsRepository.findByScanSession(currentSession)).thenReturn(Optional.empty());
            when(scanSessionRepository.findByUserOrderByScanDateDesc(user))
                    .thenReturn(List.of(currentSession));

            AnalysisResultDTO result = scanSessionService.getSessionForUser(10L, "pac@test.com");

            assertThat(result.getEvolution()).isNotNull();
            assertThat(result.getEvolution().getTrend()).isEqualTo("FIRST_SESSION");
        }

        @Test
        @DisplayName("IMPROVEMENT - current GPS lower by >5% than previous")
        void shouldReturnImprovementTrend() {
            User user = buildUser(1L, "pac@test.com", UserRole.PATIENT);
            ScanSession currentSession = buildSession(10L, user, ProcessingStatus.COMPLETED);
            ScanSession previousSession = buildSession(5L, user, ProcessingStatus.COMPLETED);

            BiomechanicsMetrics currentMetrics = buildFullMetrics(currentSession, 25.0);
            BiomechanicsMetrics previousMetrics = new BiomechanicsMetrics();
            previousMetrics.setGlobalPostureScore(BigDecimal.valueOf(40.0));

            when(userMapper.getUserByEmail("pac@test.com")).thenReturn(user);
            when(scanSessionRepository.findById(10L)).thenReturn(Optional.of(currentSession));
            when(metricsRepository.findByScanSession(currentSession)).thenReturn(Optional.of(currentMetrics));
            when(recommendationRepository.findByScanSessionOrderBySeverityDesc(currentSession))
                    .thenReturn(List.of());
            when(rawKeypointsRepository.findByScanSession(currentSession)).thenReturn(Optional.empty());
            when(scanSessionRepository.findByUserOrderByScanDateDesc(user))
                    .thenReturn(List.of(currentSession, previousSession));
            when(metricsRepository.findByScanSession(previousSession))
                    .thenReturn(Optional.of(previousMetrics));

            AnalysisResultDTO result = scanSessionService.getSessionForUser(10L, "pac@test.com");

            assertThat(result.getEvolution().getTrend()).isEqualTo("IMPROVEMENT");
        }

        @Test
        @DisplayName("DETERIORATION - current GPS higher by >5% than previous")
        void shouldReturnDeteriorationTrend() {
            User user = buildUser(1L, "pac@test.com", UserRole.PATIENT);
            ScanSession currentSession = buildSession(10L, user, ProcessingStatus.COMPLETED);
            ScanSession previousSession = buildSession(5L, user, ProcessingStatus.COMPLETED);

            BiomechanicsMetrics currentMetrics = buildFullMetrics(currentSession, 40.0);
            BiomechanicsMetrics previousMetrics = new BiomechanicsMetrics();
            previousMetrics.setGlobalPostureScore(BigDecimal.valueOf(25.0));

            when(userMapper.getUserByEmail("pac@test.com")).thenReturn(user);
            when(scanSessionRepository.findById(10L)).thenReturn(Optional.of(currentSession));
            when(metricsRepository.findByScanSession(currentSession)).thenReturn(Optional.of(currentMetrics));
            when(recommendationRepository.findByScanSessionOrderBySeverityDesc(currentSession))
                    .thenReturn(List.of());
            when(rawKeypointsRepository.findByScanSession(currentSession)).thenReturn(Optional.empty());
            when(scanSessionRepository.findByUserOrderByScanDateDesc(user))
                    .thenReturn(List.of(currentSession, previousSession));
            when(metricsRepository.findByScanSession(previousSession))
                    .thenReturn(Optional.of(previousMetrics));

            AnalysisResultDTO result = scanSessionService.getSessionForUser(10L, "pac@test.com");

            assertThat(result.getEvolution().getTrend()).isEqualTo("DETERIORATION");
        }

        @Test
        @DisplayName("STABLE - current GPS similar to previous (delta < 5%)")
        void shouldReturnStableTrend() {
            User user = buildUser(1L, "pac@test.com", UserRole.PATIENT);
            ScanSession currentSession = buildSession(10L, user, ProcessingStatus.COMPLETED);
            ScanSession previousSession = buildSession(5L, user, ProcessingStatus.COMPLETED);

            BiomechanicsMetrics currentMetrics = buildFullMetrics(currentSession, 25.5);
            BiomechanicsMetrics previousMetrics = new BiomechanicsMetrics();
            previousMetrics.setGlobalPostureScore(BigDecimal.valueOf(25.0));

            when(userMapper.getUserByEmail("pac@test.com")).thenReturn(user);
            when(scanSessionRepository.findById(10L)).thenReturn(Optional.of(currentSession));
            when(metricsRepository.findByScanSession(currentSession)).thenReturn(Optional.of(currentMetrics));
            when(recommendationRepository.findByScanSessionOrderBySeverityDesc(currentSession))
                    .thenReturn(List.of());
            when(rawKeypointsRepository.findByScanSession(currentSession)).thenReturn(Optional.empty());
            when(scanSessionRepository.findByUserOrderByScanDateDesc(user))
                    .thenReturn(List.of(currentSession, previousSession));
            when(metricsRepository.findByScanSession(previousSession))
                    .thenReturn(Optional.of(previousMetrics));

            AnalysisResultDTO result = scanSessionService.getSessionForUser(10L, "pac@test.com");

            assertThat(result.getEvolution().getTrend()).isEqualTo("STABLE");
        }

        @Test
        @DisplayName("Previous session without metrics - evolution returns null")
        void shouldReturnNullEvolutionWhenPreviousMetricsMissing() {
            User user = buildUser(1L, "pac@test.com", UserRole.PATIENT);
            ScanSession currentSession = buildSession(10L, user, ProcessingStatus.COMPLETED);
            ScanSession previousSession = buildSession(5L, user, ProcessingStatus.COMPLETED);
            BiomechanicsMetrics currentMetrics = buildFullMetrics(currentSession, 25.0);

            when(userMapper.getUserByEmail("pac@test.com")).thenReturn(user);
            when(scanSessionRepository.findById(10L)).thenReturn(Optional.of(currentSession));
            when(metricsRepository.findByScanSession(currentSession)).thenReturn(Optional.of(currentMetrics));
            when(recommendationRepository.findByScanSessionOrderBySeverityDesc(currentSession))
                    .thenReturn(List.of());
            when(rawKeypointsRepository.findByScanSession(currentSession)).thenReturn(Optional.empty());
            when(scanSessionRepository.findByUserOrderByScanDateDesc(user))
                    .thenReturn(List.of(currentSession, previousSession));
            when(metricsRepository.findByScanSession(previousSession))
                    .thenReturn(Optional.empty());

            AnalysisResultDTO result = scanSessionService.getSessionForUser(10L, "pac@test.com");

            assertThat(result.getEvolution()).isNull();
        }
    }
}
