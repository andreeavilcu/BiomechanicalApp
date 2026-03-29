package com.biomechanics.backend.repository;

import com.biomechanics.backend.model.entity.BiomechanicsMetrics;
import com.biomechanics.backend.model.entity.ScanSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface BiomechanicsMetricsRepository extends JpaRepository<BiomechanicsMetrics, Long> {
    Optional<BiomechanicsMetrics> findByScanSession(ScanSession scanSession);

    void deleteByScanSession(ScanSession scanSession);

    @Query("SELECT COUNT(m) FROM BiomechanicsMetrics m WHERE m.createdAt BETWEEN :from AND :to")
    Long countSessionsInPeriod(LocalDateTime from, LocalDateTime to);

    @Query("SELECT AVG(m.globalPostureScore) FROM BiomechanicsMetrics m WHERE m.createdAt BETWEEN :from AND :to")
    Double getAverageGps(LocalDateTime from, LocalDateTime to);

    @Query("SELECT AVG(m.fhpAngle) FROM BiomechanicsMetrics m WHERE m.createdAt BETWEEN :from AND :to")
    Double getAverageFhp(LocalDateTime from, LocalDateTime to);

    @Query("SELECT AVG(m.qAngleLeft + m.qAngleRight) / 2 FROM BiomechanicsMetrics m WHERE m.createdAt BETWEEN :from AND :to")
    Double getAverageQAngle(LocalDateTime from, LocalDateTime to);

    @Query(value = "SELECT CAST(created_at AS DATE) as date, AVG(global_posture_score) as avgGps, COUNT(*) as count " +
            "FROM biomechanics_metrics GROUP BY CAST(created_at AS DATE) ORDER BY date DESC LIMIT :days", nativeQuery = true)
    List<Object[]> getPostureTrends(int days);
}
