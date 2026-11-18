package com.makerspace.backend.services;
import com.makerspace.backend.model.HardwareDoorOverride;
import com.makerspace.backend.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
@Service @Transactional @RequiredArgsConstructor
public class HardwareDoorOverrideService {
    private final HardwareDoorOverrideRepository overrides;
    private final HardwareDeviceRepository devices;
    private final UserRepository users;
    @Transactional(readOnly=true)
    public List<HardwareDoorOverride> list(Long userId) { return overrides.findByUserIdOrderByCreatedAtDesc(userId); }
    public HardwareDoorOverride grant(Long userId, UUID deviceId, String reason, Integer minutes, Long actor) {
        if (deviceId == null || minutes == null || minutes < 1 || minutes > 10080 || reason == null || reason.trim().isEmpty() || reason.trim().length() > 200)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Select a door Pi, enter a reason (1–200 characters), and a duration from 1–10080 minutes");
        var device = devices.findLocked(deviceId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Pi not found"));
        if (!device.isEnabled() || device.getEquipmentId() != null)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose an enabled general-entry Pi without equipment binding");
        users.findById(userId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Member not found"));
        var grant = new HardwareDoorOverride();
        grant.setUserId(userId); grant.setDeviceId(deviceId); grant.setGrantedBy(actor);
        grant.setReason(reason.trim()); grant.setExpiresAt(Instant.now().plusSeconds(minutes * 60L));
        return overrides.save(grant);
    }
    public void revoke(Long userId, Long id, Long actor) {
        var grant = overrides.findById(id).filter(o -> o.getUserId().equals(userId)).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Override not found"));
        if (grant.getRevokedAt() == null) { grant.setRevokedAt(Instant.now()); grant.setRevokedBy(actor); }
    }
}
