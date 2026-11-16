package com.makerspace.backend.repository;

import com.makerspace.backend.model.HardwareDeviceEvent;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface HardwareDeviceEventRepository extends JpaRepository<HardwareDeviceEvent, Long> {
    boolean existsByDeviceIdAndEventId(UUID deviceId, UUID eventId);
    List<HardwareDeviceEvent> findAllByOrderByReceivedAtDescIdDesc(Pageable pageable);
}
