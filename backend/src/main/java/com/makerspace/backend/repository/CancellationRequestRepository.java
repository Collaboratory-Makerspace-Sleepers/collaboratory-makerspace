package com.makerspace.backend.repository;

import com.makerspace.backend.model.CancellationRequest;
import com.makerspace.backend.model.CancellationRequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CancellationRequestRepository extends JpaRepository<CancellationRequest, Long> {
    List<CancellationRequest> findAllByOrderByRequestedAtDesc();
    List<CancellationRequest> findByStatusOrderByRequestedAtAsc(CancellationRequestStatus status);
    Optional<CancellationRequest> findByReservationId(Long reservationId);
    long countByStatus(CancellationRequestStatus status);
}
