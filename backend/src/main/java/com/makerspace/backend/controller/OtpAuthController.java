package com.makerspace.backend.controller;

import com.makerspace.backend.controller.dto.OtpRequest;
import com.makerspace.backend.controller.dto.OtpVerifyRequest;
import com.makerspace.backend.services.OtpAuthService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
public class OtpAuthController {

    @Autowired
    private OtpAuthService otpAuthService;

    @PostMapping("/otp/request")
    public ResponseEntity<Map<String, String>> requestOtp(@Valid @RequestBody OtpRequest req) {
        otpAuthService.issueCode(req.email());
        return ResponseEntity.ok(Map.of("message", "A one-time code has been sent to your email"));
    }

    @PostMapping("/otp/verify")
    public ResponseEntity<Map<String, String>> verifyOtp(@Valid @RequestBody OtpVerifyRequest req) {
        return ResponseEntity.ok(otpAuthService.verifyAndLogin(req.email(), req.code()));
    }
}