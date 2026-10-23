package com.makerspace.backend.repository;

import com.makerspace.backend.model.ClassEnrollment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ClassEnrollmentRepository extends JpaRepository<ClassEnrollment, Long> {

    Optional<ClassEnrollment> findByClassSessionIdAndUserId(Long classSessionId, Long userId);

    List<ClassEnrollment> findByUserId(Long userId);

    long countByClassSessionIdAndStatus(Long classSessionId, String status);

    List<ClassEnrollment> findByClassSessionIdAndStatus(Long classSessionId, String status);
}
