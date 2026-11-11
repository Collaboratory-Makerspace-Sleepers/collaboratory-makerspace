package com.makerspace.backend.controller;

import com.makerspace.backend.controller.dto.AccessCheckRequest;
import com.makerspace.backend.controller.dto.AccessCheckResponse;
import com.makerspace.backend.services.AccessCheckService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/access")
public class AccessController {

    @Autowired private AccessCheckService accessCheckService;

    /**
     * Check-in endpoint called by staff terminals or kiosk devices.
     * Returns whether the user may access the requested equipment right now.
     *
     * Requires MANAGE_RESERVATIONS authority (staff / admin).
     */
    @PostMapping("/check")
    @PreAuthorize("hasAuthority('MANAGE_RESERVATIONS')")
    public AccessCheckResponse check(@Valid @RequestBody AccessCheckRequest request) {
        return accessCheckService.check(request.userId(), request.equipmentId());
    }
}
