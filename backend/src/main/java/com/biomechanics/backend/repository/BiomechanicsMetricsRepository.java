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

    @Query("SELECT STDDEV(m.globalPostureScore) FROM BiomechanicsMetrics m WHERE m.createdAt BETWEEN :from AND :to")
    Double getStdDevGps(LocalDateTime from, LocalDateTime to);

    @Query(value = "SELECT PERCENTILE_CONT(:percentile) WITHIN GROUP (ORDER BY global_posture_score) " +
            "FROM biomechanics_metrics WHERE created_at BETWEEN :from AND :to", nativeQuery = true)
    Double getPercentileGps(LocalDateTime from, LocalDateTime to, double percentile);

    @Query(value = "SELECT COUNT(*) FROM biomechanics_metrics", nativeQuery = true)
    Long countAllMetrics();

    @Query(value = """
            SELECT
              PERCENTILE_CONT(0.25) WITHIN GROUP (ORDER BY global_posture_score) AS p25,
              PERCENTILE_CONT(0.50) WITHIN GROUP (ORDER BY global_posture_score) AS p50,
              PERCENTILE_CONT(0.75) WITHIN GROUP (ORDER BY global_posture_score) AS p75,
              AVG(global_posture_score) AS avg
            FROM biomechanics_metrics
            WHERE global_posture_score IS NOT NULL
            """, nativeQuery = true)
    List<Object[]> getGpsBenchmark();

    @Query(value = """
            SELECT
              PERCENTILE_CONT(0.25) WITHIN GROUP (ORDER BY fhp_angle) AS p25,
              PERCENTILE_CONT(0.50) WITHIN GROUP (ORDER BY fhp_angle) AS p50,
              PERCENTILE_CONT(0.75) WITHIN GROUP (ORDER BY fhp_angle) AS p75,
              AVG(fhp_angle) AS avg
            FROM biomechanics_metrics
            WHERE fhp_angle IS NOT NULL
            """, nativeQuery = true)
    List<Object[]> getFhpBenchmark();

    @Query(value = """
            SELECT
              PERCENTILE_CONT(0.25) WITHIN GROUP (ORDER BY (q_angle_left + q_angle_right) / 2) AS p25,
              PERCENTILE_CONT(0.50) WITHIN GROUP (ORDER BY (q_angle_left + q_angle_right) / 2) AS p50,
              PERCENTILE_CONT(0.75) WITHIN GROUP (ORDER BY (q_angle_left + q_angle_right) / 2) AS p75,
              AVG((q_angle_left + q_angle_right) / 2) AS avg
            FROM biomechanics_metrics
            WHERE q_angle_left IS NOT NULL AND q_angle_right IS NOT NULL
            """, nativeQuery = true)
    List<Object[]> getQAngleBenchmark();

    @Query(value = """
            SELECT
              PERCENTILE_CONT(0.25) WITHIN GROUP (ORDER BY shoulder_asymmetry_cm) AS p25,
              PERCENTILE_CONT(0.50) WITHIN GROUP (ORDER BY shoulder_asymmetry_cm) AS p50,
              PERCENTILE_CONT(0.75) WITHIN GROUP (ORDER BY shoulder_asymmetry_cm) AS p75,
              AVG(shoulder_asymmetry_cm) AS avg
            FROM biomechanics_metrics
            WHERE shoulder_asymmetry_cm IS NOT NULL
            """, nativeQuery = true)
    List<Object[]> getShoulderAsymmetryBenchmark();
}
