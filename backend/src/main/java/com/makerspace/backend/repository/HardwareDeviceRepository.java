package com.makerspace.backend.repository;

import com.makerspace.backend.model.HardwareDevice;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface HardwareDeviceRepository extends JpaRepository<HardwareDevice, UUID> {
    List<HardwareDevice> findByDeletedAtIsNullOrderByCreatedAtDesc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from HardwareDevice d where d.id = :id and d.deletedAt is null")
    Optional<HardwareDevice> findLocked(UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from HardwareDevice d where d.pairingHash = :hash and d.deletedAt is null")
    Optional<HardwareDevice> findForPairing(String hash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from HardwareDevice d where d.connected = true and d.lastSeenAt < :before and d.deletedAt is null")
    List<HardwareDevice> findDisconnected(Instant before);
}
