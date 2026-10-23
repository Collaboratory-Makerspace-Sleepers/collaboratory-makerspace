package com.makerspace.backend.repository;

import com.makerspace.backend.model.ClassSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.ZonedDateTime;
import java.util.List;

@Repository
public interface ClassSessionRepository extends JpaRepository<ClassSession, Long> {

    @Query("SELECT cs FROM ClassSession cs WHERE cs.startTime > :now AND cs.status = 'SCHEDULED' ORDER BY cs.startTime ASC")
    List<ClassSession> findUpcoming(ZonedDateTime now);

    List<ClassSession> findByInstructorIdOrderByStartTimeDesc(Long instructorId);

    @Query("SELECT cs FROM ClassSession cs ORDER BY cs.startTime DESC")
    List<ClassSession> findAllOrderByStartTimeDesc();
}
