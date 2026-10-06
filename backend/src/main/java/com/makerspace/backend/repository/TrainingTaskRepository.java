package com.makerspace.backend.repository;

import com.makerspace.backend.model.TrainingTask;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TrainingTaskRepository extends JpaRepository<TrainingTask, Long> {

    @EntityGraph(attributePaths = "equipment")
    List<TrainingTask> findByUserIdOrderByCreatedAtDesc(Long userId);

    @EntityGraph(attributePaths = "equipment")
    Optional<TrainingTask> findById(Long id);

    Optional<TrainingTask> findByUserIdAndEquipmentId(Long userId, Long equipmentId);
}