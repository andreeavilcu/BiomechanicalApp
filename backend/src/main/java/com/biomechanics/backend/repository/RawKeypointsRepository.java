package com.biomechanics.backend.repository;

import com.biomechanics.backend.model.entity.RawKeypoints;
import com.biomechanics.backend.model.entity.ScanSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RawKeypointsRepository extends JpaRepository<RawKeypoints, Long>{
    Optional<RawKeypoints> findByScanSession(ScanSession scanSession);

    void deleteByScanSession(ScanSession scanSession);
}
