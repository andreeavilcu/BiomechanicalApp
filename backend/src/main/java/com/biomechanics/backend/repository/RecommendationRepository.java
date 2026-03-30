package com.biomechanics.backend.repository;

import com.biomechanics.backend.model.entity.Recommendation;
import com.biomechanics.backend.model.entity.ScanSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RecommendationRepository extends JpaRepository<Recommendation, Long> {
    List<Recommendation> findByScanSessionOrderBySeverityDesc(
            ScanSession scanSession);

    void deleteByScanSession(ScanSession scanSession);
}
