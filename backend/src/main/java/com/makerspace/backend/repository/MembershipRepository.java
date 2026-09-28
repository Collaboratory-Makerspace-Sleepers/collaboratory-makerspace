package com.makerspace.backend.repository;

import com.makerspace.backend.model.Membership;
import com.makerspace.backend.model.MembershipStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface MembershipRepository extends JpaRepository<Membership, Long> {
    Optional<Membership> findByUserId(Long userId);
    Optional<Membership> findByStripeSubscriptionId(String stripeSubscriptionId);
    List<Membership> findByUserIdAndStatusIn(Long userId, List<MembershipStatus> statuses);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT m FROM Membership m WHERE m.user.id = :userId AND m.status IN :statuses")
    Optional<Membership> findLiveByUserIdForUpdate(@Param("userId") Long userId,
                                                   @Param("statuses") Collection<MembershipStatus> statuses);

    List<Membership> findByStatusAndGracePeriodEndsAtLessThanEqual(MembershipStatus status, ZonedDateTime asOf);
}
