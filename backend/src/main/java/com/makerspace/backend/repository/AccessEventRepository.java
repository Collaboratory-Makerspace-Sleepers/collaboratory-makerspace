package com.makerspace.backend.repository;

import com.makerspace.backend.model.AccessEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AccessEventRepository extends JpaRepository<AccessEvent, Long> {
}
