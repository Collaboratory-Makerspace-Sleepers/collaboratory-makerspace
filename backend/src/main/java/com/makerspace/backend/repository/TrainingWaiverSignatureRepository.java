package com.makerspace.backend.repository;

import com.makerspace.backend.model.TrainingWaiverSignature;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.time.ZonedDateTime;
import java.util.Optional;

@Repository
public interface TrainingWaiverSignatureRepository extends JpaRepository<TrainingWaiverSignature, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from TrainingWaiverSignature w where w.id = :id and w.user.id = :userId and w.equipment.id = :equipmentId")
    Optional<TrainingWaiverSignature> findForBooking(
            @Param("id") Long id, @Param("userId") Long userId, @Param("equipmentId") Long equipmentId);

    /**
     * Used by ReservationService to validate the waiver before booking.
     * reservationId IS NULL ensures the signature has not already been consumed by another reservation.
     */
    Optional<TrainingWaiverSignature> findByIdAndUserIdAndEquipmentIdAndReservationIdIsNull(
            Long id, Long userId, Long equipmentId);

    Optional<TrainingWaiverSignature>
            findFirstByUserIdAndEquipmentIdAndStartTimeAndEndTimeAndReservationIdIsNullOrderByIdDesc(
                    Long userId, Long equipmentId, ZonedDateTime startTime, ZonedDateTime endTime);

    boolean existsByUserIdAndEquipmentId(Long userId, Long equipmentId);

    boolean existsByUserId(Long userId);
}
