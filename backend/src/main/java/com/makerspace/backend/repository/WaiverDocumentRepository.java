package com.makerspace.backend.repository;

import com.makerspace.backend.model.WaiverDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface WaiverDocumentRepository extends JpaRepository<WaiverDocument, Long> {
    boolean existsByCheckInId(Long checkInId);
}
