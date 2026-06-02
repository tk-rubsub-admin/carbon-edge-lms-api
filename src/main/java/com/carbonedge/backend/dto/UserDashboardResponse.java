package com.carbonedge.backend.dto;

import java.util.List;

public record UserDashboardResponse(
        UserProfileResponse user,
        Integer totalCourses,
        List<CourseResponse> courses
) {
    public static UserDashboardResponse of(UserProfileResponse user, List<CourseResponse> courses) {
        return new UserDashboardResponse(user, courses.size(), courses);
    }
}
