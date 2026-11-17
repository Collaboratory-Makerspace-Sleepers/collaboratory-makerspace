package com.makerspace.backend.repository;

import com.makerspace.backend.model.AccessEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface AccessEventRepository extends JpaRepository<AccessEvent, Long> {

    @Query(nativeQuery = true, value = """
            SELECT DATE_TRUNC('day', occurred_at) AS day, COUNT(*)
            FROM access_events WHERE granted=true AND occurred_at >= :since
            GROUP BY day ORDER BY day
            """)
    List<Object[]> dailyGrantedVisits(@Param("since") Instant since);
}
