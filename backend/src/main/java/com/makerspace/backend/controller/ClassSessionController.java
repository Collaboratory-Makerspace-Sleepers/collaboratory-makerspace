package com.makerspace.backend.controller;

import com.makerspace.backend.config.security.UserPrincipal;
import com.makerspace.backend.controller.dto.*;
import com.makerspace.backend.services.ClassSessionService;
import com.stripe.exception.StripeException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/class-sessions")
public class ClassSessionController {

    @Autowired
    private ClassSessionService classSessionService;

    // ── Public listing ────────────────────────────────────────────────────────

    @GetMapping
    public List<ClassSessionDTO> list(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(defaultValue = "false") boolean all) {
        Long userId = principal != null ? principal.userId() : null;
        return all ? classSessionService.listAll(userId) : classSessionService.listUpcoming(userId);
    }

    @GetMapping("/{id}")
    public ClassSessionDTO get(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        Long userId = principal != null ? principal.userId() : null;
        return classSessionService.get(id, userId);
    }

    // ── Authenticated user endpoints ──────────────────────────────────────────

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public List<ClassEnrollmentDTO> myEnrollments(@AuthenticationPrincipal UserPrincipal principal) {
        return classSessionService.getMyEnrollments(principal.userId());
    }

    @PostMapping("/{id}/enroll")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, Object>> enroll(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) throws StripeException {
        Map<String, Object> result = classSessionService.enroll(id, principal.userId());
        return ResponseEntity.ok(result);
    }

    @PostMapping("/enrollment/{enrollmentId}/confirm")
    @PreAuthorize("isAuthenticated()")
    public ClassEnrollmentDTO confirmEnrollment(
            @PathVariable Long enrollmentId,
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody Map<String, String> body) throws StripeException {
        String stripeSessionId = body.get("stripeSessionId");
        return classSessionService.confirmEnrollment(enrollmentId, principal.userId(), stripeSessionId);
    }

    @DeleteMapping("/{id}/enroll")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> cancelEnrollment(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        classSessionService.cancelEnrollment(id, principal.userId());
        return ResponseEntity.noContent().build();
    }

    // ── Admin / Staff / Instructor endpoints ──────────────────────────────────

    @PostMapping
    @PreAuthorize("hasAuthority('MANAGE_CLASSES')")
    public ClassSessionDTO create(
            @RequestBody CreateClassSessionRequest req,
            @AuthenticationPrincipal UserPrincipal principal) {
        return classSessionService.create(req, principal.userId());
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('MANAGE_CLASSES')")
    public ClassSessionDTO update(
            @PathVariable Long id,
            @RequestBody UpdateClassSessionRequest req,
            @AuthenticationPrincipal UserPrincipal principal) {
        return classSessionService.update(id, req, principal.userId(), true);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('MANAGE_CLASSES')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        classSessionService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/roster")
    @PreAuthorize("hasAuthority('MANAGE_CLASSES')")
    public List<Map<String, Object>> roster(@PathVariable Long id) {
        return classSessionService.getRoster(id);
    }
}
