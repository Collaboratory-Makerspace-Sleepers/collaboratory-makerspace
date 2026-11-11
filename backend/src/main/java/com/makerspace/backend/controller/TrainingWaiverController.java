package com.makerspace.backend.controller;

import com.makerspace.backend.config.security.UserSecurity;
import com.makerspace.backend.controller.dto.SignTrainingWaiverRequest;
import com.makerspace.backend.controller.dto.WaiverSignedResponse;
import com.makerspace.backend.services.TrainingWaiverService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/training-waivers")
public class TrainingWaiverController {

    @Autowired private TrainingWaiverService trainingWaiverService;
    @Autowired private UserSecurity userSecurity;

    /**
     * Signs a training waiver for a specific equipment + time window. Must be called
     * before POST /reservations. The returned waiverSignatureId is required to create
     * the reservation.
     */
    @PostMapping("/{equipmentId}/sign")
    @ResponseStatus(HttpStatus.CREATED)
    public WaiverSignedResponse sign(@PathVariable Long equipmentId,
                                     @Valid @RequestBody SignTrainingWaiverRequest request,
                                     Authentication auth) {
        Long waiverSignatureId = trainingWaiverService.sign(
                userSecurity.getUserId(auth),
                equipmentId,
                request.signerName(),
                request.startTime(),
                request.endTime(),
                request.waiverVersion()
        ).getId();
        return new WaiverSignedResponse(waiverSignatureId);
    }
}
