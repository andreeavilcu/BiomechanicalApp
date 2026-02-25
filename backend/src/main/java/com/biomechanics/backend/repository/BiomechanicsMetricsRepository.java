package com.biomechanics.backend.repository;

import com.biomechanics.backend.model.entity.BiomechanicsMetrics;
import com.biomechanics.backend.model.entity.ScanSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface BiomechanicsMetricsRepository extends JpaRepository<BiomechanicsMetrics, Long> {
    Optional<BiomechanicsMetrics> findByScanSession(ScanSession scanSession);

    void deleteByScanSession(ScanSession scanSession);
}
