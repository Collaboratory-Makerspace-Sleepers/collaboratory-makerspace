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
    /**
     * Returns the most relevant membership for a user: live statuses (ACTIVE, TRIALING,
     * PAST_DUE, GRACE) are preferred over terminal ones, and within each group the most
     * recently updated row wins. A user may have multiple terminal rows from past subscriptions.
     */
    @Query(value = """
            SELECT * FROM membership
            WHERE user_id = :userId
            ORDER BY
                CASE status
                    WHEN 'ACTIVE'    THEN 0
                    WHEN 'TRIALING'  THEN 1
                    WHEN 'PAST_DUE'  THEN 2
                    WHEN 'GRACE'     THEN 3
                    ELSE 4
                END,
                updated_at DESC
            LIMIT 1
            """, nativeQuery = true)
    Optional<Membership> findByUserId(@Param("userId") Long userId);
    Optional<Membership> findByStripeSubscriptionId(String stripeSubscriptionId);
    List<Membership> findByUserIdAndStatusIn(Long userId, List<MembershipStatus> statuses);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT m FROM Membership m WHERE m.user.id = :userId AND m.status IN :statuses")
    Optional<Membership> findLiveByUserIdForUpdate(@Param("userId") Long userId,
                                                   @Param("statuses") Collection<MembershipStatus> statuses);

    List<Membership> findByStatusAndGracePeriodEndsAtLessThanEqual(MembershipStatus status, ZonedDateTime asOf);
}
