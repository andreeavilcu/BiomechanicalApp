package com.biomechanics.backend.repository;

import com.biomechanics.backend.model.entity.ScanSession;
import com.biomechanics.backend.model.entity.User;
import com.biomechanics.backend.model.enums.ProcessingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ScanSessionRepository extends JpaRepository<ScanSession, Long>{
    List<ScanSession> findByUserOrderByScanDateDesc(User user);

    List<ScanSession> findByUserAndProcessingStatus(User user, ProcessingStatus status);

    @Query("SELECT s FROM ScanSession s WHERE s.user = :user " +
            "AND s.processingStatus = 'COMPLETED' " +
            "ORDER BY s.scanDate DESC")
    Optional<ScanSession> findLatestCompletedSession(@Param("user") User user);

    List<ScanSession> findByUserAndScanDateBetween(User user, LocalDateTime startDate, LocalDateTime endDate);

    long countByUserAndProcessingStatus(User user, ProcessingStatus status);
}
