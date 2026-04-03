package com.biomechanics.backend.service;

import com.biomechanics.backend.mapper.RecommendationMapper;
import com.biomechanics.backend.mapper.UserMapper;
import com.biomechanics.backend.model.dto.AnalysisResultDTO;
import com.biomechanics.backend.model.dto.PythonResponseDTO;
import com.biomechanics.backend.model.dto.RecommendationDTO;
import com.biomechanics.backend.model.entity.*;
import com.biomechanics.backend.model.enums.MetricType;
import com.biomechanics.backend.model.enums.ProcessingStatus;
import com.biomechanics.backend.model.enums.UserRole;
import com.biomechanics.backend.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScanSessionService {
    private final ScanSessionRepository scanSessionRepository;
    private final BiomechanicsMetricsRepository metricsRepository;
    private final RawKeypointsRepository rawKeypointsRepository;
    private final RecommendationRepository recommendationRepository;
    private final UserRepository userRepository;
    private final PythonIntegrationService pythonIntegrationService;
    private final BiomechanicsService biomechanicsService;
    private final RecommendationService recommendationService;
    private final UserMapper userMapper;
    private final RecommendationMapper recommendationMapper;

    @Transactional
    public AnalysisResultDTO processScan(
            MultipartFile file,
            Long userId,
            Double heightCm,
            String scanType
    ) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User with ID " + userId + " does not exist."));

        validateFile(file);

        ScanSession session = new ScanSession();
        session.setUser(user);
        session.setScanFilePath(file.getOriginalFilename());
        session.setScanType(scanType);
        session.setProcessingStatus(ProcessingStatus.PENDING);
        session.setTargetHeightMeters(
                BigDecimal.valueOf(heightCm / 100.0).setScale(2, RoundingMode.HALF_UP)
        );
        ScanSession savedSession = scanSessionRepository.save(session);
        log.info("Scan session created: ID={} for user={}", savedSession.getId(), user.getEmail());

        try {
            savedSession.setProcessingStatus(ProcessingStatus.PROCESSING);
            scanSessionRepository.save(savedSession);

            log.info("Sending scan to Python service for session ID={}", savedSession.getId());
            PythonResponseDTO pythonResponse = pythonIntegrationService
                    .processScanFile(file, heightCm);

            if (pythonResponse.getMeta() != null) {
                savedSession.setPythonMethod(pythonResponse.getMeta().getMethod());
                if (pythonResponse.getMeta().getBestScore() != null) {
                    savedSession.setAiConfidenceScore(
                            BigDecimal.valueOf(pythonResponse.getMeta().getBestScore())
                                    .setScale(3, RoundingMode.HALF_UP)
                    );
                }
                if (pythonResponse.getMeta().getScalingFactor() != null) {
                    savedSession.setScalingFactor(
                            BigDecimal.valueOf(pythonResponse.getMeta().getScalingFactor())
                                    .setScale(4, RoundingMode.HALF_UP)
                    );
                }
            }

            RawKeypoints rawKeypoints = mapToRawKeypoints(pythonResponse, savedSession);
            rawKeypointsRepository.save(rawKeypoints);

            BiomechanicsMetrics metrics = biomechanicsService
                    .calculateMetrics(pythonResponse, user);
            metrics.setScanSession(savedSession);
            metricsRepository.save(metrics);

            recommendationService.generateAndSave(savedSession, metrics, user);

            savedSession.setProcessingStatus(ProcessingStatus.COMPLETED);
            scanSessionRepository.save(savedSession);
            log.info("Scan processed successfully: session ID={}, GPS={}, Risk={}",
                    savedSession.getId(), metrics.getGlobalPostureScore(), metrics.getRiskLevel());

            return buildAnalysisResult(savedSession, metrics, user);

        } catch (Exception e) {
            savedSession.setProcessingStatus(ProcessingStatus.FAILED);
            savedSession.setErrorMessage(e.getMessage());
            scanSessionRepository.save(savedSession);
            log.error("Scan processing failed for session ID={}: {}", savedSession.getId(), e.getMessage());
            throw new RuntimeException("Scan processing failed: " + e.getMessage(), e);
        }
    }

    public List<AnalysisResultDTO> getHistoryByEmail(String email) {
        User user = userMapper.getUserByEmail(email);
        List<ScanSession> sessions = scanSessionRepository
                .findByUserOrderByScanDateDesc(user);

        return sessions.stream()
                .map(session -> {
                    Optional<BiomechanicsMetrics> metrics = metricsRepository
                            .findByScanSession(session);
                    return buildAnalysisResult(session, metrics.orElse(null), user);
                })
                .collect(Collectors.toList());
    }

    public AnalysisResultDTO getSessionForUser(Long sessionId, String requesterEmail) {
        User requester = userMapper.getUserByEmail(requesterEmail);
        ScanSession session = scanSessionRepository.findById(sessionId)
                .orElseThrow(() -> new RuntimeException("Session ID=" + sessionId + " does not exist."));

        checkReadAccess(requester, session);

        Optional<BiomechanicsMetrics> metrics = metricsRepository.findByScanSession(session);
        return buildAnalysisResult(session, metrics.orElse(null), session.getUser());
    }

    public List<AnalysisResultDTO> getHistoryByUserId(Long targetUserId, String requesterEmail) {
        User requester = userMapper.getUserByEmail(requesterEmail);
        User targetUser = userRepository.findById(targetUserId)
                .orElseThrow(() -> new RuntimeException("User ID=" + targetUserId + " does not exist."));

        if (requester.getRole() == UserRole.SPECIALIST) {
            boolean isAssigned = scanSessionRepository
                    .existsByUserAndSpecialist(targetUser, requester);
            if (!isAssigned) {
                throw new AccessDeniedException(
                        "Patient ID=" + targetUserId + " is not assigned to this specialist."
                );
            }
        }

        List<ScanSession> sessions = scanSessionRepository
                .findByUserOrderByScanDateDesc(targetUser);

        return sessions.stream()
                .map(session -> {
                    Optional<BiomechanicsMetrics> metrics = metricsRepository
                            .findByScanSession(session);
                    return buildAnalysisResult(session, metrics.orElse(null), targetUser);
                })
                .collect(Collectors.toList());
    }

    @Transactional
    public void deleteSession(Long sessionId, String requesterEmail) {
        User requester = userMapper.getUserByEmail(requesterEmail);
        ScanSession session = scanSessionRepository.findById(sessionId)
                .orElseThrow(() -> new RuntimeException("Session ID=" + sessionId + " does not exist."));

        boolean isOwner = session.getUser().getEmail().equals(requesterEmail);
        boolean isAdmin = requester.getRole() == UserRole.ADMIN;

        if (!isOwner && !isAdmin) {
            throw new AccessDeniedException("You do not have permission to delete this session.");
        }

        recommendationRepository.deleteByScanSession(session);
        metricsRepository.deleteByScanSession(session);
        rawKeypointsRepository.deleteByScanSession(session);
        scanSessionRepository.delete(session);
        log.info("Session ID={} deleted by {}", sessionId, requesterEmail);
    }

    public boolean isOwner(String email, Long userId) {
        return userRepository.findByEmail(email)
                .map(user -> user.getId().equals(userId))
                .orElse(false);
    }

    private RawKeypoints mapToRawKeypoints(PythonResponseDTO dto, ScanSession session) {
        RawKeypoints kp = new RawKeypoints();
        kp.setScanSession(session);

        if (dto.getNose() != null) {
            kp.setNoseX(bd(dto.getNose().getX()));
            kp.setNoseY(bd(dto.getNose().getY()));
            kp.setNoseZ(bd(dto.getNose().getZ()));
        }
        if (dto.getLEar() != null) {
            kp.setLEarX(bd(dto.getLEar().getX()));
            kp.setLEarY(bd(dto.getLEar().getY()));
            kp.setLEarZ(bd(dto.getLEar().getZ()));
        }
        if (dto.getREar() != null) {
            kp.setREarX(bd(dto.getREar().getX()));
            kp.setREarY(bd(dto.getREar().getY()));
            kp.setREarZ(bd(dto.getREar().getZ()));
        }
        if (dto.getNeck() != null) {
            kp.setNeckX(bd(dto.getNeck().getX()));
            kp.setNeckY(bd(dto.getNeck().getY()));
            kp.setNeckZ(bd(dto.getNeck().getZ()));
        }
        if (dto.getLShoulder() != null) {
            kp.setLShoulderX(bd(dto.getLShoulder().getX()));
            kp.setLShoulderY(bd(dto.getLShoulder().getY()));
            kp.setLShoulderZ(bd(dto.getLShoulder().getZ()));
        }
        if (dto.getRShoulder() != null) {
            kp.setRShoulderX(bd(dto.getRShoulder().getX()));
            kp.setRShoulderY(bd(dto.getRShoulder().getY()));
            kp.setRShoulderZ(bd(dto.getRShoulder().getZ()));
        }
        if (dto.getLHip() != null) {
            kp.setLHipX(bd(dto.getLHip().getX()));
            kp.setLHipY(bd(dto.getLHip().getY()));
            kp.setLHipZ(bd(dto.getLHip().getZ()));
        }
        if (dto.getRHip() != null) {
            kp.setRHipX(bd(dto.getRHip().getX()));
            kp.setRHipY(bd(dto.getRHip().getY()));
            kp.setRHipZ(bd(dto.getRHip().getZ()));
        }
        if (dto.getPelvis() != null) {
            kp.setPelvisX(bd(dto.getPelvis().getX()));
            kp.setPelvisY(bd(dto.getPelvis().getY()));
            kp.setPelvisZ(bd(dto.getPelvis().getZ()));
        }
        if (dto.getLKnee() != null) {
            kp.setLKneeX(bd(dto.getLKnee().getX()));
            kp.setLKneeY(bd(dto.getLKnee().getY()));
            kp.setLKneeZ(bd(dto.getLKnee().getZ()));
        }
        if (dto.getRKnee() != null) {
            kp.setRKneeX(bd(dto.getRKnee().getX()));
            kp.setRKneeY(bd(dto.getRKnee().getY()));
            kp.setRKneeZ(bd(dto.getRKnee().getZ()));
        }
        if (dto.getLAnkle() != null) {
            kp.setLAnkleX(bd(dto.getLAnkle().getX()));
            kp.setLAnkleY(bd(dto.getLAnkle().getY()));
            kp.setLAnkleZ(bd(dto.getLAnkle().getZ()));
        }
        if (dto.getRAnkle() != null) {
            kp.setRAnkleX(bd(dto.getRAnkle().getX()));
            kp.setRAnkleY(bd(dto.getRAnkle().getY()));
            kp.setRAnkleZ(bd(dto.getRAnkle().getZ()));
        }

        return kp;
    }

    private AnalysisResultDTO buildAnalysisResult(
            ScanSession session,
            BiomechanicsMetrics metrics,
            User user
    ) {
        AnalysisResultDTO.AnalysisResultDTOBuilder builder = AnalysisResultDTO.builder()
                .sessionId(session.getId())
                .scanDate(session.getScanDate())
                .status(session.getProcessingStatus())
                .errorMessage(session.getErrorMessage())
                .processingMethod(session.getPythonMethod())
                .aiConfidenceScore(session.getAiConfidenceScore())
                .scalingFactor(session.getScalingFactor());

        if (metrics != null) {
            builder
                    .qAngleLeft(metrics.getQAngleLeft())
                    .qAngleRight(metrics.getQAngleRight())
                    .fhpAngle(metrics.getFhpAngle())
                    .fhpDistanceCm(metrics.getFhpDistanceCm())
                    .shoulderAsymmetryCm(metrics.getShoulderAsymmetryCm())
                    .stancePhaseLeft(metrics.getStancePhaseLeft())
                    .stancePhaseRight(metrics.getStancePhaseRight())
                    .globalPostureScore(metrics.getGlobalPostureScore())
                    .riskLevel(metrics.getRiskLevel())
                    .evolution(calculateEvolution(user, metrics, session));

            List<Recommendation> recs = recommendationRepository
                    .findByScanSessionOrderBySeverityDesc(session);

            List<RecommendationDTO> recDTOs = recs.stream()
                    .map(recommendationMapper::toDTO)
                    .collect(Collectors.toList());

            builder.recommendations(recDTOs);

            recs.stream()
                    .filter(r -> r.getMetricType() == MetricType.GLOBAL)
                    .findFirst()
                    .ifPresent(globalRec -> {
                        builder.globalFeedback(globalRec.getBiomechanicalCause());
                        builder.medicalDisclaimer(globalRec.getDisclaimerRequired());
                    });
        } else {
            builder.recommendations(Collections.emptyList());
        }

        return builder.build();
    }

    private AnalysisResultDTO.EvolutionDTO calculateEvolution(
            User user,
            BiomechanicsMetrics currentMetrics,
            ScanSession currentSession
    ) {
        List<ScanSession> previousSessions = scanSessionRepository
                .findByUserOrderByScanDateDesc(user)
                .stream()
                .filter(s -> s.getProcessingStatus() == ProcessingStatus.COMPLETED
                        && !s.getId().equals(currentSession.getId()))
                .collect(Collectors.toList());

        if (previousSessions.isEmpty() || currentMetrics == null) {
            return AnalysisResultDTO.EvolutionDTO.builder()
                    .trend("FIRST_SESSION")
                    .build();
        }

        ScanSession previousSession = previousSessions.get(0);
        Optional<BiomechanicsMetrics> previousMetricsOpt = metricsRepository
                .findByScanSession(previousSession);

        if (previousMetricsOpt.isEmpty()) {
            return null;
        }

        BiomechanicsMetrics previousMetrics = previousMetricsOpt.get();

        double currentGps = currentMetrics.getGlobalPostureScore().doubleValue();
        double previousGps = previousMetrics.getGlobalPostureScore().doubleValue();

        double deltaPercent = previousGps != 0
                ? ((currentGps - previousGps) / previousGps) * 100
                : 0;

        String trend;
        if (deltaPercent < -5) {
            trend = "IMPROVEMENT";
        } else if (deltaPercent > 5) {
            trend = "DETERIORATION";
        } else {
            trend = "STABLE";
        }

        long daysBetween = ChronoUnit.DAYS.between(
                previousSession.getScanDate(),
                currentSession.getScanDate()
        );

        return AnalysisResultDTO.EvolutionDTO.builder()
                .postureScoreChange(
                        BigDecimal.valueOf(deltaPercent).setScale(2, RoundingMode.HALF_UP)
                )
                .trend(trend)
                .daysSinceLastScan((int) daysBetween)
                .build();
    }

    private void checkReadAccess(User requester, ScanSession session) {
        switch (requester.getRole()) {
            case ADMIN -> { }
            case PATIENT -> {
                if (!session.getUser().getId().equals(requester.getId())) {
                    throw new AccessDeniedException(
                            "You do not have access to this session."
                    );
                }
            }
            case SPECIALIST -> {
                boolean isOwner = session.getUser().getId().equals(requester.getId());
                boolean isAssigned = scanSessionRepository
                        .existsByUserAndSpecialist(session.getUser(), requester);
                if (!isOwner && !isAssigned) {
                    throw new AccessDeniedException(
                            "Patient is not assigned to this specialist."
                    );
                }
            }
            case RESEARCHER -> throw new AccessDeniedException(
                    "Researchers do not have access to individual sessions."
            );
        }
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File is empty or missing.");
        }
        String filename = file.getOriginalFilename();
        if (filename == null ||
                (!filename.toLowerCase().endsWith(".ply") &&
                        !filename.toLowerCase().endsWith(".pcd"))) {
            throw new IllegalArgumentException(
                    "Invalid file format. Only .ply and .pcd files are accepted."
            );
        }
        if (file.getSize() > 500L * 1024 * 1024) {
            throw new IllegalArgumentException(
                    "File size exceeds the maximum limit of 500MB."
            );
        }
    }

    private BigDecimal bd(Double value) {
        if (value == null) return null;
        return BigDecimal.valueOf(value).setScale(4, RoundingMode.HALF_UP);
    }
}