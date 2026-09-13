package com.makerspace.backend.repository;

import com.makerspace.backend.model.EventProcessingStatus;
import com.makerspace.backend.model.StripeEventLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StripeEventLogRepository extends JpaRepository<StripeEventLog, String> {
    List<StripeEventLog> findByStatus(EventProcessingStatus status);
    boolean existsByEventId(String eventId);
}
