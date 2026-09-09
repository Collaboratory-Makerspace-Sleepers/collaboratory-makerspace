package com.makerspace.backend.controller;

import com.makerspace.backend.controller.dto.LoginRequest;
import com.makerspace.backend.controller.dto.RegisterRequest;
import com.makerspace.backend.controller.dto.UserDTO;
import com.makerspace.backend.services.SimpleAuthService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class SimpleAuthController {

    @Autowired
    private SimpleAuthService simpleAuthService;

    @PostMapping("/register")
    public ResponseEntity<UserDTO> register(@Valid @RequestBody RegisterRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                UserDTO.from(simpleAuthService.register(req.firstName(), req.lastName(), req.email(), req.password())));
    }

    @PostMapping("/login")
    public ResponseEntity<UserDTO> login(@Valid @RequestBody LoginRequest req) {
        return ResponseEntity.ok(UserDTO.from(simpleAuthService.login(req.email(), req.password())));
    }
}