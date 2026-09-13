package com.makerspace.backend.repository;

import com.makerspace.backend.model.Membership;
import com.makerspace.backend.model.MembershipStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MembershipRepository extends JpaRepository<Membership, Long> {
    List<Membership> findByUserId(Long userId);
    Optional<Membership> findByStripeSubscriptionId(String stripeSubscriptionId);
    List<Membership> findByUserIdAndStatusIn(Long userId, List<MembershipStatus> statuses);
}
