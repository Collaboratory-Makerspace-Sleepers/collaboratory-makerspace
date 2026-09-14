package com.makerspace.backend.repository;

import com.makerspace.backend.model.EventProcessingStatus;
import com.makerspace.backend.model.StripeEventLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StripeEventLogRepository extends JpaRepository<StripeEventLog, String> {

    List<StripeEventLog> findByStatus(EventProcessingStatus status);

    boolean existsByEventId(String eventId);

    @Query(value = "SELECT MAX(received_at) FROM stripe_event_log", nativeQuery = true)
    Optional<java.time.ZonedDateTime> findMaxReceivedAt();

    /**
     * Native insert so a PK violation on duplicate event_id throws DataIntegrityViolationException
     * without poisoning the JPA persistence context.
     */
    @Modifying
    @Query(value = """
            INSERT INTO stripe_event_log (event_id, event_type, api_version, livemode, status, source, attempt_count)
            VALUES (:eventId, :type, :apiVersion, :livemode, 'RECEIVED', :source, 0)
            """, nativeQuery = true)
    void insertReceived(@Param("eventId") String eventId,
                        @Param("type") String type,
                        @Param("apiVersion") String apiVersion,
                        @Param("livemode") boolean livemode,
                        @Param("source") String source);

    @Modifying
    @Query(value = "UPDATE stripe_event_log SET status = 'PROCESSED', processed_at = now() WHERE event_id = :eventId",
            nativeQuery = true)
    void markProcessed(@Param("eventId") String eventId);

    @Modifying
    @Query(value = "UPDATE stripe_event_log SET status = 'SKIPPED', processed_at = now() WHERE event_id = :eventId",
            nativeQuery = true)
    void markSkipped(@Param("eventId") String eventId);
}
