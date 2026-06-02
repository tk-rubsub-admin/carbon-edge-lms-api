package com.carbonedge.backend.service;

import com.carbonedge.backend.dto.CourseResponse;
import com.carbonedge.backend.dto.UserDashboardResponse;
import com.carbonedge.backend.dto.UserProfileResponse;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserDashboardService {

    private final AuthService authService;
    private final CourseService courseService;

    public UserDashboardService(AuthService authService, CourseService courseService) {
        this.authService = authService;
        this.courseService = courseService;
    }

    @Transactional(readOnly = true)
    public UserDashboardResponse getDashboard(String authorizationHeader) {
        UserProfileResponse user = authService.currentUser(authorizationHeader);
        List<CourseResponse> courses = courseService.getMyCourses(authorizationHeader);
        return UserDashboardResponse.of(user, courses);
    }
}
