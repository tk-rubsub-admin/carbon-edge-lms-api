package com.carbonedge.backend.controller;

import com.carbonedge.backend.dto.UserProfileResponse;
import com.carbonedge.backend.service.AuthService;
import com.carbonedge.backend.dto.UserDashboardResponse;
import com.carbonedge.backend.service.UserDashboardService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/users")
public class UserController {

    private final AuthService authService;
    private final UserDashboardService userDashboardService;

    public UserController(AuthService authService, UserDashboardService userDashboardService) {
        this.authService = authService;
        this.userDashboardService = userDashboardService;
    }

    @GetMapping("/me")
    public UserProfileResponse currentUser(@RequestHeader("Authorization") String authorizationHeader) {
        return authService.currentUser(authorizationHeader);
    }

    @GetMapping("/me/dashboard")
    public UserDashboardResponse dashboard(@RequestHeader("Authorization") String authorizationHeader) {
        return userDashboardService.getDashboard(authorizationHeader);
    }
}
