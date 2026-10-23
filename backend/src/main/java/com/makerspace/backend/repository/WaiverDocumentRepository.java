package com.makerspace.backend.repository;

import com.makerspace.backend.model.WaiverDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface WaiverDocumentRepository extends JpaRepository<WaiverDocument, Long> {
    boolean existsByCheckInId(Long checkInId);
}
