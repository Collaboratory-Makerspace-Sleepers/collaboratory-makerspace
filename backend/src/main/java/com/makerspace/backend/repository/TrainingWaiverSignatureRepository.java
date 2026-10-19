package com.makerspace.backend.repository;

import com.makerspace.backend.model.TrainingWaiverSignature;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TrainingWaiverSignatureRepository extends JpaRepository<TrainingWaiverSignature, Long> {

    /**
     * Used by ReservationService to validate the waiver before booking.
     * reservationId IS NULL ensures the signature has not already been consumed by another reservation.
     */
    Optional<TrainingWaiverSignature> findByIdAndUserIdAndEquipmentIdAndReservationIdIsNull(
            Long id, Long userId, Long equipmentId);

    boolean existsByUserIdAndEquipmentId(Long userId, Long equipmentId);

    boolean existsByUserId(Long userId);
}
