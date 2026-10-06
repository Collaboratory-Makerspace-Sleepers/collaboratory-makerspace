package com.makerspace.backend.controller;

import com.makerspace.backend.config.security.UserSecurity;
import com.makerspace.backend.controller.dto.SignTrainingWaiverRequest;
import com.makerspace.backend.services.TrainingWaiverService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/training-waivers")
public class TrainingWaiverController {

    @Autowired private TrainingWaiverService trainingWaiverService;
    @Autowired private UserSecurity userSecurity;

    @PostMapping("/{equipmentId}/sign")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void sign(@PathVariable Long equipmentId,
                     @Valid @RequestBody SignTrainingWaiverRequest request,
                     Authentication auth) {
        trainingWaiverService.sign(
                userSecurity.getUserId(auth), equipmentId, request.signerName());
    }
}