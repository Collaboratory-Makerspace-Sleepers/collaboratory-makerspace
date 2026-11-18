package com.makerspace.backend.controller;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.makerspace.backend.config.security.DevicePrincipal;
import com.makerspace.backend.config.security.UserPrincipal;
import com.makerspace.backend.services.HardwareDeviceService;
import com.makerspace.backend.services.HardwareDeviceService.*;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

@RestController
@RequestMapping("/api/v1/hardware")
@RequiredArgsConstructor
@Slf4j
public class HardwareDeviceController {
    private final HardwareDeviceService service;
    private final Cache<String, AtomicInteger> pairingAttempts = Caffeine.newBuilder()
            .maximumSize(10000).expireAfterWrite(Duration.ofMinutes(1)).build();

    public record PairRequest(String code, String hostname) {}
    public record HeartbeatRequest(String hostname, String readerStatus) {}
    public record EventsRequest(List<EventInput> events) {}
    public record AssignmentRequest(Long userId, String label) {}

    @GetMapping("/device/protocol")
    public Map<String, Object> protocol() {
        return Map.of("protocol", "makerspace-device-v1", "version", 1);
    }

    @PostMapping("/device/pair")
    public Map<String, Object> pair(@RequestBody PairRequest input, HttpServletRequest request) {
        if (pairingAttempts.get(request.getRemoteAddr(), ignored -> new AtomicInteger()).incrementAndGet() > 10) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Too many pairing attempts; wait one minute");
        }
        return service.pair(input.code(), input.hostname());
    }

    @PostMapping("/device/heartbeat")
    public Map<String, Object> heartbeat(@AuthenticationPrincipal DevicePrincipal principal,
                                       @RequestBody HeartbeatRequest input) {
        return service.heartbeat(requireDevice(principal), input.hostname(), input.readerStatus());
    }

    @PostMapping("/device/events")
    public Map<String, Object> events(@AuthenticationPrincipal DevicePrincipal principal,
                                    @RequestBody EventsRequest input) {
        return service.ingest(requireDevice(principal), input.events());
    }

    private DevicePrincipal requireDevice(DevicePrincipal principal) {
        if (principal == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Pair this Pi first");
        return principal;
    }

    @GetMapping("/devices")
    @PreAuthorize("hasAuthority('MANAGE_EQUIPMENT')")
    public List<DeviceView> list() {
        return service.list();
    }

    @PostMapping("/devices")
    @PreAuthorize("hasAuthority('MANAGE_EQUIPMENT')")
    public Pairing create(@RequestBody DeviceSettings input) {
        return service.create(input);
    }

    @PatchMapping("/devices/{id}")
    @PreAuthorize("hasAuthority('MANAGE_EQUIPMENT')")
    public DeviceView update(@PathVariable UUID id, @RequestBody DeviceSettings input) {
        return service.update(id, input);
    }

    @PostMapping("/devices/{id}/pairing-code")
    @PreAuthorize("hasAuthority('MANAGE_EQUIPMENT')")
    public Pairing pairingCode(@PathVariable UUID id) {
        return service.pairingCode(id);
    }

    @DeleteMapping("/devices/{id}")
    @PreAuthorize("hasAuthority('MANAGE_EQUIPMENT')")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/devices/{id}/assignment")
    @PreAuthorize("hasAuthority('MANAGE_EQUIPMENT')")
    public DeviceView assignment(@PathVariable UUID id, @RequestBody AssignmentRequest input,
                                 @AuthenticationPrincipal UserPrincipal principal) {
        return service.startAssignment(id, input.userId(), input.label(), principal.userId());
    }

    @DeleteMapping("/devices/{id}/assignment")
    @PreAuthorize("hasAuthority('MANAGE_EQUIPMENT')")
    public DeviceView cancelAssignment(@PathVariable UUID id) {
        return service.cancelAssignment(id);
    }

    @GetMapping("/devices/events")
    @PreAuthorize("hasAuthority('MANAGE_EQUIPMENT')")
    public List<EventView> recent(@RequestParam(defaultValue = "50") int limit) {
        return service.recent(limit);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleError(ResponseStatusException error) {
        log.warn("Hardware device request rejected: status={} reason={}", error.getStatusCode(), error.getReason());
        return ResponseEntity.status(error.getStatusCode()).body(Map.of("message",
                error.getReason() == null ? "Hardware device request failed" : error.getReason()));
    }
}
