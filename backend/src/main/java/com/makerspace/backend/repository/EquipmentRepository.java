package com.makerspace.backend.repository;

import com.makerspace.backend.model.Equipment;
import com.makerspace.backend.model.EquipmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EquipmentRepository extends JpaRepository<Equipment, Long> {
    List<Equipment> findByStatus(EquipmentStatus status);
    List<Equipment> findByCategory(String category);
    List<Equipment> findByNameContainingIgnoreCase(String name);
}
