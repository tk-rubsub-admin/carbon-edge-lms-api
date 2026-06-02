package com.carbonedge.backend.controller;

import com.carbonedge.backend.dto.AuthResponse;
import com.carbonedge.backend.dto.LoginRequest;
import com.carbonedge.backend.dto.MoodleSessionResponse;
import com.carbonedge.backend.dto.RegisterRequest;
import com.carbonedge.backend.service.AuthService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        log.info("Login request for identifier={}", request.usernameOrEmail());
        return authService.login(request);
    }

    @PostMapping("/moodle-session")
    public MoodleSessionResponse createMoodleSession(@RequestHeader("Authorization") String authorizationHeader) {
        return authService.createMoodleSession(authorizationHeader);
    }
}
