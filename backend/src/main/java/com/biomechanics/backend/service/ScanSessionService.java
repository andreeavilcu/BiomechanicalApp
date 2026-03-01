package com.biomechanics.backend.service;

import com.biomechanics.backend.model.dto.AnalysisResultDTO;
import com.biomechanics.backend.model.dto.BiomechanicsMetricsDTO;
import com.biomechanics.backend.model.dto.PythonResponseDTO;
import com.biomechanics.backend.model.dto.ScanUploadRequestDTO;
import com.biomechanics.backend.model.entity.*;
import com.biomechanics.backend.model.enums.ProcessingStatus;
import com.biomechanics.backend.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScanSessionService {

    private final ScanSessionRepository scanSessionRepository;
    private final RawKeypointsRepository rawKeypointsRepository;
    private final BiomechanicsMetricsRepository biomechanicsMetricsRepository;
    private final UserRepository userRepository;

    private final PythonIntegrationService pythonIntegrationService;
    private final BiomechanicsService biomechanicsService;

    private static final String UPLOAD_DIR = "uploads/scans/";

    @Transactional
    public AnalysisResultDTO processScanUpload(ScanUploadRequestDTO request) throws IOException {
        log.info("Starting scan processing workflow for user {}", request.getUserId());

        User user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "User not found with ID: " + request.getUserId()
                ));

        String tempFilePath = saveScanFileWithTempId(request.getFile());
        log.info("Saved scan file temporarily: {}", tempFilePath);

        ScanSession session = createInitialSession(request, user);
        session.setScanFilePath(tempFilePath);
        session = scanSessionRepository.save(session);
        log.info("Created scan session {}", session.getId());

        try {
            String finalFilePath = renameScanFile(tempFilePath, session.getId());
            session.setScanFilePath(finalFilePath);
            session.setProcessingStatus(ProcessingStatus.PROCESSING);
            session = scanSessionRepository.save(session);

            PythonResponseDTO pythonResponse = pythonIntegrationService.processScanFile(
                    request.getFile(),
                    request.getHeightCm()
            );

            updateSessionWithPythonMetadata(session, pythonResponse);
            session = scanSessionRepository.save(session);

            RawKeypoints rawKeypoints = saveRawKeypoints(pythonResponse, session);
            log.info("Saved raw keypoints for session {}", session.getId());

            BiomechanicsMetrics metrics = biomechanicsService.calculateMetrics(
                    pythonResponse,
                    user
            );
            metrics.setScanSession(session);
            metrics = biomechanicsMetricsRepository.save(metrics);
            log.info("Calculated and saved biomechanics metrics for session {}", session.getId());

            session.setProcessingStatus(ProcessingStatus.COMPLETED);
            session = scanSessionRepository.save(session);

            AnalysisResultDTO result = buildAnalysisResult(session, metrics, user);

            log.info("Scan processing completed successfully for session {}", session.getId());
            return result;

        } catch (Exception e) {
            log.error("Scan processing failed for session {}: {}", session.getId(), e.getMessage(), e);
            session.setProcessingStatus(ProcessingStatus.FAILED);
            session.setErrorMessage(e.getMessage());
            scanSessionRepository.save(session);

            throw new RuntimeException("Failed to process scan: " + e.getMessage(), e);
        }
    }

    private ScanSession createInitialSession(ScanUploadRequestDTO request, User user) {
        ScanSession session = new ScanSession();
        session.setUser(user);
        session.setScanType(request.getScanType());
        session.setProcessingStatus(ProcessingStatus.PENDING);
        session.setTargetHeightMeters(
                BigDecimal.valueOf(request.getHeightCm() / 100.0)
        );
        session.setScanDate(LocalDateTime.now());
        return session;
    }

    private String saveScanFileWithTempId(org.springframework.web.multipart.MultipartFile file)
            throws IOException {

        Path uploadPath = Paths.get(UPLOAD_DIR);
        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
            log.info("Created upload directory: {}", uploadPath.toAbsolutePath());
        }

        String originalFilename = file.getOriginalFilename();
        String extension = originalFilename != null && originalFilename.contains(".")
                ? originalFilename.substring(originalFilename.lastIndexOf("."))
                : ".ply";

        String tempFilename = String.format("temp_%d_%s%s",
                System.currentTimeMillis(),
                UUID.randomUUID().toString().substring(0, 8),
                extension
        );

        Path filePath = uploadPath.resolve(tempFilename);

        Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

        String savedPath = filePath.toString();
        log.info("Saved scan file with temp ID: {}", savedPath);

        return savedPath;
    }

    private String renameScanFile(String tempFilePath, Long sessionId) throws IOException {
        Path tempPath = Paths.get(tempFilePath);

        if (!Files.exists(tempPath)) {
            log.warn("Temp file not found, keeping temp path: {}", tempFilePath);
            return tempFilePath;
        }

        String tempFilename = tempPath.getFileName().toString();
        String extension = tempFilename.contains(".")
                ? tempFilename.substring(tempFilename.lastIndexOf("."))
                : ".ply";

        String finalFilename = String.format("%d_%s%s",
                sessionId,
                UUID.randomUUID().toString(),
                extension
        );

        Path finalPath = tempPath.getParent().resolve(finalFilename);

        Files.move(tempPath, finalPath, StandardCopyOption.REPLACE_EXISTING);

        String finalPathStr = finalPath.toString();
        log.info("Renamed file from {} to {}", tempFilePath, finalPathStr);

        return finalPathStr;
    }

    private String saveScanFile(org.springframework.web.multipart.MultipartFile file, Long sessionId)
            throws IOException {

        Path uploadPath = Paths.get(UPLOAD_DIR);
        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
            log.info("Created upload directory: {}", uploadPath.toAbsolutePath());
        }

        String originalFilename = file.getOriginalFilename();
        String extension = originalFilename != null && originalFilename.contains(".")
                ? originalFilename.substring(originalFilename.lastIndexOf("."))
                : ".ply";

        String uniqueFilename = String.format("%d_%s%s",
                sessionId,
                UUID.randomUUID().toString(),
                extension
        );

        Path filePath = uploadPath.resolve(uniqueFilename);

        Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

        String savedPath = filePath.toString();
        log.info("Saved scan file: {}", savedPath);

        return savedPath;
    }

    private void updateSessionWithPythonMetadata(ScanSession session, PythonResponseDTO response) {
        if (response.getMeta() != null) {
            session.setPythonMethod(response.getMeta().getMethod());
            session.setAiConfidenceScore(
                    response.getMeta().getBestScore() != null
                            ? BigDecimal.valueOf(response.getMeta().getBestScore())
                            : null
            );
            session.setScalingFactor(
                    response.getMeta().getScalingFactor() != null
                            ? BigDecimal.valueOf(response.getMeta().getScalingFactor())
                            : null
            );
        }
    }

    private RawKeypoints saveRawKeypoints(PythonResponseDTO response, ScanSession session) {
        RawKeypoints keypoints = new RawKeypoints();
        keypoints.setScanSession(session);

        if (response.getNose() != null) {
            keypoints.setNoseX(toBigDecimal(response.getNose().getX()));
            keypoints.setNoseY(toBigDecimal(response.getNose().getY()));
            keypoints.setNoseZ(toBigDecimal(response.getNose().getZ()));
        }

        if (response.getLEar() != null) {
            keypoints.setLEarX(toBigDecimal(response.getLEar().getX()));
            keypoints.setLEarY(toBigDecimal(response.getLEar().getY()));
            keypoints.setLEarZ(toBigDecimal(response.getLEar().getZ()));
        }

        if (response.getREar() != null) {
            keypoints.setREarX(toBigDecimal(response.getREar().getX()));
            keypoints.setREarY(toBigDecimal(response.getREar().getY()));
            keypoints.setREarZ(toBigDecimal(response.getREar().getZ()));
        }

        if (response.getNeck() != null) {
            keypoints.setNeckX(toBigDecimal(response.getNeck().getX()));
            keypoints.setNeckY(toBigDecimal(response.getNeck().getY()));
            keypoints.setNeckZ(toBigDecimal(response.getNeck().getZ()));
        }

        if (response.getLShoulder() != null) {
            keypoints.setLShoulderX(toBigDecimal(response.getLShoulder().getX()));
            keypoints.setLShoulderY(toBigDecimal(response.getLShoulder().getY()));
            keypoints.setLShoulderZ(toBigDecimal(response.getLShoulder().getZ()));
        }

        if (response.getRShoulder() != null) {
            keypoints.setRShoulderX(toBigDecimal(response.getRShoulder().getX()));
            keypoints.setRShoulderY(toBigDecimal(response.getRShoulder().getY()));
            keypoints.setRShoulderZ(toBigDecimal(response.getRShoulder().getZ()));
        }

        if (response.getLHip() != null) {
            keypoints.setLHipX(toBigDecimal(response.getLHip().getX()));
            keypoints.setLHipY(toBigDecimal(response.getLHip().getY()));
            keypoints.setLHipZ(toBigDecimal(response.getLHip().getZ()));
        }

        if (response.getRHip() != null) {
            keypoints.setRHipX(toBigDecimal(response.getRHip().getX()));
            keypoints.setRHipY(toBigDecimal(response.getRHip().getY()));
            keypoints.setRHipZ(toBigDecimal(response.getRHip().getZ()));
        }

        if (response.getPelvis() != null) {
            keypoints.setPelvisX(toBigDecimal(response.getPelvis().getX()));
            keypoints.setPelvisY(toBigDecimal(response.getPelvis().getY()));
            keypoints.setPelvisZ(toBigDecimal(response.getPelvis().getZ()));
        }

        if (response.getLKnee() != null) {
            keypoints.setLKneeX(toBigDecimal(response.getLKnee().getX()));
            keypoints.setLKneeY(toBigDecimal(response.getLKnee().getY()));
            keypoints.setLKneeZ(toBigDecimal(response.getLKnee().getZ()));
        }

        if (response.getRKnee() != null) {
            keypoints.setRKneeX(toBigDecimal(response.getRKnee().getX()));
            keypoints.setRKneeY(toBigDecimal(response.getRKnee().getY()));
            keypoints.setRKneeZ(toBigDecimal(response.getRKnee().getZ()));
        }

        if (response.getLAnkle() != null) {
            keypoints.setLAnkleX(toBigDecimal(response.getLAnkle().getX()));
            keypoints.setLAnkleY(toBigDecimal(response.getLAnkle().getY()));
            keypoints.setLAnkleZ(toBigDecimal(response.getLAnkle().getZ()));
        }

        if (response.getRAnkle() != null) {
            keypoints.setRAnkleX(toBigDecimal(response.getRAnkle().getX()));
            keypoints.setRAnkleY(toBigDecimal(response.getRAnkle().getY()));
            keypoints.setRAnkleZ(toBigDecimal(response.getRAnkle().getZ()));
        }

        return rawKeypointsRepository.save(keypoints);
    }

    private AnalysisResultDTO buildAnalysisResult(
            ScanSession session,
            BiomechanicsMetrics metrics,
            User user) {

        AnalysisResultDTO result = AnalysisResultDTO.builder()
                .sessionId(session.getId())
                .scanDate(session.getScanDate())
                .status(session.getProcessingStatus())
                .errorMessage(session.getErrorMessage())
                .processingMethod(session.getPythonMethod())
                .aiConfidenceScore(session.getAiConfidenceScore())
                .scalingFactor(session.getScalingFactor())
                .qAngleLeft(metrics.getQAngleLeft())
                .qAngleRight(metrics.getQAngleRight())
                .fhpAngle(metrics.getFhpAngle())
                .fhpDistanceCm(metrics.getFhpDistanceCm())
                .shoulderAsymmetryCm(metrics.getShoulderAsymmetryCm())
                .globalPostureScore(metrics.getGlobalPostureScore())
                .riskLevel(metrics.getRiskLevel())
                .build();

        List<String> recommendations = biomechanicsService.generateRecommendations(metrics);
        result.setRecommendations(recommendations);

        AnalysisResultDTO.EvolutionDTO evolution = calculateEvolution(user, metrics);
        result.setEvolution(evolution);

        return result;
    }

    private AnalysisResultDTO.EvolutionDTO calculateEvolution(User user, BiomechanicsMetrics currentMetrics) {
        try {
            ScanSession previousSession = scanSessionRepository.findLatestCompletedSession(user)
                    .orElse(null);

            if (previousSession == null || previousSession.getId().equals(currentMetrics.getScanSession().getId())) {
                return AnalysisResultDTO.EvolutionDTO.builder()
                        .postureScoreChange(BigDecimal.ZERO)
                        .trend("BASELINE")
                        .daysSinceLastScan(0)
                        .build();
            }

            BiomechanicsMetrics previousMetrics = biomechanicsMetricsRepository
                    .findByScanSession(previousSession)
                    .orElse(null);

            if (previousMetrics == null) {
                return null;
            }

            BigDecimal currentGPS = currentMetrics.getGlobalPostureScore();
            BigDecimal previousGPS = previousMetrics.getGlobalPostureScore();
            BigDecimal change = currentGPS.subtract(previousGPS);

            String trend;
            if (change.abs().compareTo(BigDecimal.valueOf(5)) < 0) {
                trend = "STABLE";
            } else if (change.compareTo(BigDecimal.ZERO) < 0) {
                trend = "IMPROVING";
            } else {
                trend = "DECLINING";
            }

            long daysSince = ChronoUnit.DAYS.between(
                    previousSession.getScanDate(),
                    currentMetrics.getScanSession().getScanDate()
            );

            return AnalysisResultDTO.EvolutionDTO.builder()
                    .postureScoreChange(change)
                    .trend(trend)
                    .daysSinceLastScan((int) daysSince)
                    .build();

        } catch (Exception e) {
            log.error("Failed to calculate evolution: {}", e.getMessage(), e);
            return null;
        }
    }

    public AnalysisResultDTO getAnalysisResults(Long sessionId) {
        ScanSession session = scanSessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));

        BiomechanicsMetrics metrics = biomechanicsMetricsRepository.findByScanSession(session)
                .orElse(null);

        if (metrics == null) {
            return AnalysisResultDTO.builder()
                    .sessionId(session.getId())
                    .scanDate(session.getScanDate())
                    .status(session.getProcessingStatus())
                    .errorMessage(session.getErrorMessage())
                    .build();
        }

        return buildAnalysisResult(session, metrics, session.getUser());
    }

    public List<BiomechanicsMetricsDTO> getUserScanHistory(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        List<ScanSession> sessions = scanSessionRepository
                .findByUserOrderByScanDateDesc(user);

        return sessions.stream()
                .map(session -> biomechanicsMetricsRepository.findByScanSession(session).orElse(null))
                .filter(metrics -> metrics != null)
                .map(this::toMetricsDTO)
                .collect(Collectors.toList());
    }

    private BiomechanicsMetricsDTO toMetricsDTO(BiomechanicsMetrics metrics) {
        return BiomechanicsMetricsDTO.builder()
                .sessionId(metrics.getScanSession().getId())
                .scanDate(metrics.getScanSession().getScanDate())
                .qAngleLeft(metrics.getQAngleLeft())
                .qAngleRight(metrics.getQAngleRight())
                .fhpAngle(metrics.getFhpAngle())
                .shoulderAsymmetryCm(metrics.getShoulderAsymmetryCm())
                .globalPostureScore(metrics.getGlobalPostureScore())
                .riskLevel(metrics.getRiskLevel())
                .build();
    }

    private BigDecimal toBigDecimal(Double value) {
        return value != null
                ? BigDecimal.valueOf(value).setScale(4, java.math.RoundingMode.HALF_UP)
                : null;
    }
}
