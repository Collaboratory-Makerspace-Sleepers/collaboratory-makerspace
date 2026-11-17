package com.makerspace.backend.repository;

import com.makerspace.backend.model.SubscriptionAuditMismatch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.ZonedDateTime;
import java.util.List;

@Repository
public interface SubscriptionAuditMismatchRepository extends JpaRepository<SubscriptionAuditMismatch, Long> {

    List<SubscriptionAuditMismatch> findByResolvedFalseOrderByAuditRunAtDesc();

    @Modifying
    @Query("UPDATE SubscriptionAuditMismatch m SET m.resolved = true, m.resolvedAt = :now WHERE m.id = :id")
    void resolve(@Param("id") Long id, @Param("now") ZonedDateTime now);
}
