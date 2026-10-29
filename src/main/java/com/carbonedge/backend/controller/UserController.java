package com.carbonedge.backend.controller;

import com.carbonedge.backend.dto.UserProfileResponse;
import com.carbonedge.backend.dto.UpdateStudentProfileRequest;
import com.carbonedge.backend.service.AuthService;
import com.carbonedge.backend.dto.UserDashboardResponse;
import com.carbonedge.backend.service.UserDashboardService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final AuthService authService;
    private final UserDashboardService userDashboardService;

    @GetMapping("/me")
    public UserProfileResponse currentUser(@RequestHeader("Authorization") String authorizationHeader) {
        return authService.currentUser(authorizationHeader);
    }

    @PutMapping("/me")
    public UserProfileResponse updateCurrentStudent(
            @RequestHeader("Authorization") String authorizationHeader,
            @Valid @RequestBody UpdateStudentProfileRequest request
    ) {
        return authService.updateStudentProfile(authorizationHeader, request);
    }

    @GetMapping("/me/dashboard")
    public UserDashboardResponse dashboard(@RequestHeader("Authorization") String authorizationHeader) {
        return userDashboardService.getDashboard(authorizationHeader);
    }
}
