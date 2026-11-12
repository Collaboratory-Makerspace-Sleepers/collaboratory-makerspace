package com.makerspace.backend.repository;

import com.makerspace.backend.model.HardwareCheckIn;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface HardwareCheckInRepository extends JpaRepository<HardwareCheckIn, Long> {

    @Query("SELECT h FROM HardwareCheckIn h ORDER BY COALESCE(h.updatedAt, h.checkinTime) DESC")
    Page<HardwareCheckIn> findRecentActivity(Pageable pageable);

    long countByStatus(HardwareCheckIn.CheckInStatus status);
    long countByStatusIn(Collection<HardwareCheckIn.CheckInStatus> statuses);

    Page<HardwareCheckIn> findByUserIdOrderByCheckinTimeDesc(Long userId, Pageable pageable);

    Page<HardwareCheckIn> findByEquipmentIdOrderByCheckinTimeDesc(Long equipmentId, Pageable pageable);

    Page<HardwareCheckIn> findByStatusOrderByCheckinTimeDesc(HardwareCheckIn.CheckInStatus status, Pageable pageable);

    List<HardwareCheckIn> findByStatusAndWaiverApprovedByAdminIsTrueOrderByCheckinTimeDesc(HardwareCheckIn.CheckInStatus status);

    Page<HardwareCheckIn> findByCheckinTimeBetweenOrderByCheckinTimeDesc(
            Instant startTime, Instant endTime, Pageable pageable);

    @Query("SELECT h FROM HardwareCheckIn h WHERE h.status IN ('NEEDS_WAIVER', 'WAIVER_SIGNED')")
    Page<HardwareCheckIn> findPendingWaivers(Pageable pageable);

    Optional<HardwareCheckIn> findByDeviceIdAndUserIdOrderByCheckinTimeDesc(String deviceId, Long userId);

    Optional<HardwareCheckIn> findFirstByUserIdAndEquipmentIdAndStatusInOrderByCheckinTimeDesc(
            Long userId, Long equipmentId, Collection<HardwareCheckIn.CheckInStatus> statuses);

    List<HardwareCheckIn> findByUserIdAndStatusInAndCheckinTimeAfterOrderByCheckinTimeDesc(
            Long userId, Collection<HardwareCheckIn.CheckInStatus> statuses, Instant after);

    List<HardwareCheckIn> findByUserIdAndEquipmentIdAndStatusInOrderByCheckinTimeDesc(
            Long userId, Long equipmentId, Collection<HardwareCheckIn.CheckInStatus> statuses);
}
