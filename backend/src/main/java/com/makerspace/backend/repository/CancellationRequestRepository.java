package com.makerspace.backend.repository;

import com.makerspace.backend.controller.dto.MemberCancellationRequestDTO;
import com.makerspace.backend.model.CancellationRequest;
import com.makerspace.backend.model.CancellationRequestStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface CancellationRequestRepository extends JpaRepository<CancellationRequest, Long> {
    List<CancellationRequest> findAllByOrderByRequestedAtDesc();
    List<CancellationRequest> findByStatusOrderByRequestedAtAsc(CancellationRequestStatus status);

    @EntityGraph(attributePaths = {"reservation.equipment", "requestedBy"})
    List<CancellationRequest> findAllByOrderByRequestedAtDescIdDesc(Pageable pageable);

    @EntityGraph(attributePaths = {"reservation.equipment", "reservation.user"})
    List<CancellationRequest> findByResolvedAtIsNotNullOrderByResolvedAtDescIdDesc(Pageable pageable);

    @Query("""
            select new com.makerspace.backend.controller.dto.MemberCancellationRequestDTO(
                request.id, reservation.id, equipment.name, reservation.startTime,
                reservation.status, request.status, request.rejectionReason, request.requestedAt, request.resolvedAt)
            from CancellationRequest request
            join request.reservation reservation
            join reservation.equipment equipment
            where reservation.userId = :userId
            order by request.requestedAt desc, request.id desc
            """)
    List<MemberCancellationRequestDTO> findHistoryByReservationOwner(@Param("userId") Long userId);

    List<CancellationRequest> findByReservationIdInAndStatusOrderByIdDesc(
            Collection<Long> reservationIds, CancellationRequestStatus status);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select request from CancellationRequest request where request.id = :id")
    Optional<CancellationRequest> findByIdForUpdate(@Param("id") Long id);
    boolean existsByReservationIdAndStatusIn(Long reservationId, Collection<CancellationRequestStatus> statuses);
    Optional<CancellationRequest> findFirstByReservationIdAndStatusOrderByIdDesc(
            Long reservationId, CancellationRequestStatus status);
    long countByStatus(CancellationRequestStatus status);
}
