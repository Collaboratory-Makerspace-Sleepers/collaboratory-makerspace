package com.makerspace.backend.controller;

import com.makerspace.backend.controller.dto.CheckinRequest;
import com.makerspace.backend.controller.dto.CheckinResponse;
import com.makerspace.backend.services.HardwareCheckinService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/hardware")
public class HardwareCheckinController {

    @Autowired private HardwareCheckinService hardwareCheckinService;

    /**
     * NFC/RFID check-in endpoint called by the door reader.
     *
     * Auth: {@code X-Device-Key} header (resolved by {@code DeviceKeyFilter}).
     * No JWT required — hardware devices do not authenticate as users.
     *
     * Request:  {@code { "cardId": "<nfc_uid>" }}
     * Response: {@code { "granted": true|false, "reason": "...", "userName": "..." }}
     */
    @PostMapping("/checkin")
    @PreAuthorize("hasAuthority('SCOPE_HARDWARE')")
    public CheckinResponse checkin(@Valid @RequestBody CheckinRequest request) {
        return hardwareCheckinService.checkin(request.cardId());
    }
}
