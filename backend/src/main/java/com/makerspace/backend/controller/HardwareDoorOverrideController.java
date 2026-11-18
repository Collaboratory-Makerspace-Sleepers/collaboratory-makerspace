package com.makerspace.backend.controller;
import com.makerspace.backend.config.security.UserPrincipal;
import com.makerspace.backend.model.HardwareDoorOverride;
import com.makerspace.backend.services.HardwareDoorOverrideService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
@RestController @RequiredArgsConstructor
@RequestMapping("/api/v1/hardware/members/{userId}/door-overrides")
@PreAuthorize("hasAuthority('MANAGE_USERS') and hasAuthority('MANAGE_EQUIPMENT')")
public class HardwareDoorOverrideController {
    private final HardwareDoorOverrideService service;
    public record Grant(UUID deviceId, String reason, Integer minutes) {}
    @GetMapping public List<HardwareDoorOverride> list(@PathVariable Long userId) { return service.list(userId); }
    @PostMapping public HardwareDoorOverride grant(@PathVariable Long userId, @RequestBody Grant input, @AuthenticationPrincipal UserPrincipal actor) {
        return service.grant(userId, input.deviceId(), input.reason(), input.minutes(), actor.userId());
    }
    @DeleteMapping("/{id}") public ResponseEntity<Void> revoke(@PathVariable Long userId, @PathVariable Long id, @AuthenticationPrincipal UserPrincipal actor) {
        service.revoke(userId, id, actor.userId()); return ResponseEntity.noContent().build();
    }
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String,String>> error(ResponseStatusException e) {
        return ResponseEntity.status(e.getStatusCode()).body(Map.of("message", e.getReason() == null ? "Override request failed" : e.getReason()));
    }
}
