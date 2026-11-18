package com.makerspace.backend.repository;

import com.makerspace.backend.model.ClassEnrollment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.time.ZonedDateTime;

@Repository
public interface ClassEnrollmentRepository extends JpaRepository<ClassEnrollment, Long> {

    @Query("""
            SELECT COUNT(e) FROM ClassEnrollment e
            WHERE e.status = 'ENROLLED' AND e.classSession.status = 'SCHEDULED'
              AND e.classSession.endTime > :now
            """)
    long countUpcoming(@Param("now") ZonedDateTime now);

    Optional<ClassEnrollment> findByClassSessionIdAndUserId(Long classSessionId, Long userId);

    List<ClassEnrollment> findByUserId(Long userId);

    long countByClassSessionIdAndStatus(Long classSessionId, String status);

    List<ClassEnrollment> findByClassSessionIdAndStatus(Long classSessionId, String status);
}
