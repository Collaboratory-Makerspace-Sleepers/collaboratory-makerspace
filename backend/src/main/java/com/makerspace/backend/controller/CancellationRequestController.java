package com.makerspace.backend.controller;

import com.makerspace.backend.config.security.UserPrincipal;
import com.makerspace.backend.config.security.UserSecurity;
import com.makerspace.backend.controller.dto.CancellationRequestDTO;
import com.makerspace.backend.model.CancellationRequestStatus;
import com.makerspace.backend.model.Permission;
import com.makerspace.backend.services.CancellationRequestService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
public class CancellationRequestController {

    @Autowired private CancellationRequestService cancellationRequestService;
    @Autowired private UserSecurity userSecurity;

    // -------------------------------------------------------------------------
    // User: submit a cancellation/refund request
    // -------------------------------------------------------------------------

    @PostMapping("/api/v1/reservations/{reservationId}/cancellation-request")
    @ResponseStatus(HttpStatus.CREATED)
    public CancellationRequestDTO submit(
            @PathVariable Long reservationId,
            @RequestBody(required = false) Map<String, String> body,
            Authentication auth) {
        UserPrincipal principal = userSecurity.getPrincipal(auth);
        String reason = body != null ? body.get("reason") : null;
        return cancellationRequestService.submit(reservationId, reason, principal);
    }

    // -------------------------------------------------------------------------
    // Admin: list
    // -------------------------------------------------------------------------

    @GetMapping("/api/v1/admin/cancellation-requests")
    @PreAuthorize("hasAuthority('" + Permission.MANAGE_RESERVATIONS + "')")
    public List<CancellationRequestDTO> list(@RequestParam(defaultValue = "ALL") String status) {
        if ("ALL".equalsIgnoreCase(status)) {
            return cancellationRequestService.listAll();
        }
        CancellationRequestStatus parsed = CancellationRequestStatus.valueOf(status.toUpperCase());
        return cancellationRequestService.listByStatus(parsed);
    }

    // -------------------------------------------------------------------------
    // Admin: count
    // -------------------------------------------------------------------------

    @GetMapping("/api/v1/admin/cancellation-requests/count")
    @PreAuthorize("hasAuthority('" + Permission.MANAGE_RESERVATIONS + "')")
    public Map<String, Long> count(@RequestParam(defaultValue = "PENDING") String status) {
        CancellationRequestStatus parsed = CancellationRequestStatus.valueOf(status.toUpperCase());
        long count = cancellationRequestService.countPending();
        // For status other than PENDING we still call countPending but the parameter is preserved
        // for future flexibility; currently only PENDING is meaningful.
        if (parsed != CancellationRequestStatus.PENDING) {
            count = cancellationRequestService.listByStatus(parsed).size();
        }
        return Map.of("count", count);
    }

    // -------------------------------------------------------------------------
    // Admin: resolve
    // -------------------------------------------------------------------------

    @PostMapping("/api/v1/admin/cancellation-requests/{id}/resolve")
    @PreAuthorize("hasAuthority('" + Permission.MANAGE_RESERVATIONS + "')")
    public CancellationRequestDTO resolve(
            @PathVariable Long id,
            @RequestBody Map<String, String> body,
            Authentication auth) {
        UserPrincipal principal = userSecurity.getPrincipal(auth);
        String decision   = body.get("decision");
        String adminNote  = body.get("adminNote");
        return cancellationRequestService.adminResolve(id, decision, adminNote, principal);
    }
}
