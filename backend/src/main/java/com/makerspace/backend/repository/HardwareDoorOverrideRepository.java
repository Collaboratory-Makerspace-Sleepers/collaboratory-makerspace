package com.makerspace.backend.repository;
import com.makerspace.backend.model.HardwareDoorOverride;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
public interface HardwareDoorOverrideRepository extends JpaRepository<HardwareDoorOverride, Long> {
    List<HardwareDoorOverride> findByUserIdOrderByCreatedAtDesc(Long userId);
    Optional<HardwareDoorOverride> findFirstByUserIdAndDeviceIdAndRevokedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(Long userId, UUID deviceId, Instant now);
}
