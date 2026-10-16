package com.makerspace.backend.repository;

import com.makerspace.backend.model.StudioAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface StudioAssignmentRepository extends JpaRepository<StudioAssignment, Long> {

    Optional<StudioAssignment> findByUserId(Long userId);

    boolean existsByUserId(Long userId);

    boolean existsByEquipmentId(Long equipmentId);

    void deleteByMembershipId(Long membershipId);
}
