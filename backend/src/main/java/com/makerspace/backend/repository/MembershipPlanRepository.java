package com.makerspace.backend.repository;

import com.makerspace.backend.model.MembershipPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MembershipPlanRepository extends JpaRepository<MembershipPlan, Long> {
    Optional<MembershipPlan> findByCode(String code);
    Optional<MembershipPlan> findByStripePriceId(String stripePriceId);
    List<MembershipPlan> findByActiveTrue();
}
