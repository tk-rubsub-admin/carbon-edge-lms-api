package com.carbonedge.backend.controller;

import com.carbonedge.backend.dto.AuthResponse;
import com.carbonedge.backend.dto.AccountAvailabilityResponse;
import com.carbonedge.backend.dto.EmailDispatchResponse;
import com.carbonedge.backend.dto.ForgotPasswordRequest;
import com.carbonedge.backend.dto.LoginRequest;
import com.carbonedge.backend.dto.MoodleSessionResponse;
import com.carbonedge.backend.dto.RegistrationResponse;
import com.carbonedge.backend.dto.RegisterRequest;
import com.carbonedge.backend.dto.ResetPasswordRequest;
import com.carbonedge.backend.dto.RefreshTokenRequest;
import com.carbonedge.backend.dto.ResendVerificationRequest;
import com.carbonedge.backend.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public RegistrationResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    @GetMapping("/availability")
    public AccountAvailabilityResponse availability(
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String email) {
        return authService.availability(username, email);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        log.info("Login request for identifier={}", request.usernameOrEmail());
        return authService.login(request);
    }

    @PostMapping("/refresh")
    public AuthResponse refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return authService.refresh(request.refreshToken());
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@RequestHeader("Authorization") String authorizationHeader) {
        authService.logout(authorizationHeader);
    }

    @PostMapping("/moodle-session")
    public MoodleSessionResponse createMoodleSession(@RequestHeader("Authorization") String authorizationHeader) {
        return authService.createMoodleSession(authorizationHeader);
    }

    @GetMapping("/verify-email")
    public AuthResponse verifyEmail(@RequestParam("token") String token) {
        return authService.verifyEmail(token);
    }

    @PostMapping("/resend-verification")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public EmailDispatchResponse resendVerification(@Valid @RequestBody ResendVerificationRequest request) {
        return authService.resendVerificationEmail(request);
    }

    @PostMapping("/forgot-password")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public EmailDispatchResponse forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        return authService.forgotPassword(request);
    }

    @PostMapping("/reset-password")
    public AuthResponse resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        return authService.resetPassword(request);
    }
}
