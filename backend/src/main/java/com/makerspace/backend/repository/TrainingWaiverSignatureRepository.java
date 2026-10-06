package com.makerspace.backend.repository;

import com.makerspace.backend.model.TrainingWaiverSignature;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TrainingWaiverSignatureRepository extends JpaRepository<TrainingWaiverSignature, Long> {
    Optional<TrainingWaiverSignature> findByUserIdAndEquipmentId(Long userId, Long equipmentId);

    boolean existsByUserIdAndEquipmentId(Long userId, Long equipmentId);
}