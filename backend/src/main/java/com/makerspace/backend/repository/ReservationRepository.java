package com.makerspace.backend.repository;

import com.makerspace.backend.model.EquipmentReservation;
import com.makerspace.backend.model.ReservationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.ZonedDateTime;
import java.util.List;

@Repository
public interface ReservationRepository extends JpaRepository<EquipmentReservation, Long> {

    List<EquipmentReservation> findByUserIdOrderByStartTimeDesc(Long userId);

    Page<EquipmentReservation> findAllByOrderByStartTimeDesc(Pageable pageable);

    List<EquipmentReservation> findByEquipmentIdOrderByStartTimeDesc(Long equipmentId);

    /**
     * Returns any ACTIVE reservations for the given equipment that overlap [startTime, endTime).
     * Two intervals overlap when: existingStart < newEnd AND existingEnd > newStart.
     * Excludes a specific reservation ID so extend operations can check without self-conflict.
     */
    @Query("""
            SELECT r FROM EquipmentReservation r
            WHERE r.equipmentId = :equipmentId
              AND r.status = :status
              AND r.startTime < :endTime
              AND r.endTime > :startTime
              AND (:excludeId IS NULL OR r.id <> :excludeId)
            """)
    List<EquipmentReservation> findOverlapping(
            @Param("equipmentId") Long equipmentId,
            @Param("startTime") ZonedDateTime startTime,
            @Param("endTime") ZonedDateTime endTime,
            @Param("status") ReservationStatus status,
            @Param("excludeId") Long excludeId
    );
}