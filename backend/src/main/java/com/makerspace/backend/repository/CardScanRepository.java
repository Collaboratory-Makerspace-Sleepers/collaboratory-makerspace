package com.makerspace.backend.repository;

import com.makerspace.backend.model.CardScan;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CardScanRepository extends JpaRepository<CardScan, Long> {
    List<CardScan> findAllByOrderByScannedAtDesc(Pageable pageable);
}
