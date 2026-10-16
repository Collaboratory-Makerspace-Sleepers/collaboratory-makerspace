package com.makerspace.backend.repository;

import com.makerspace.backend.model.TrainingCompletion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TrainingCompletionRepository extends JpaRepository<TrainingCompletion, Long> {

    boolean existsByUserIdAndEquipmentId(Long userId, Long equipmentId);

    Optional<TrainingCompletion> findByUserIdAndEquipmentId(Long userId, Long equipmentId);

    List<TrainingCompletion> findByUserId(Long userId);
}
