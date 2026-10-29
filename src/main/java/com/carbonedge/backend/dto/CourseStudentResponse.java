package com.carbonedge.backend.dto;

import com.carbonedge.backend.model.CourseStudentEnrollment;
import java.time.Instant;

public record CourseStudentResponse(
        Long userId,
        String username,
        String email,
        String firstName,
        String lastName,
        String role,
        String enrollmentStatus,
        Instant courseStartedAt,
        Instant courseCompletedAt,
        int completedRequiredItems,
        int totalRequiredItems,
        int progressPercent
) {
    public static CourseStudentResponse from(
            CourseStudentEnrollment enrollment,
            int completedRequiredItems,
            int totalRequiredItems
    ) {
        int progressPercent = totalRequiredItems == 0
                ? ("COMPLETED".equals(enrollment.getStatus().name()) ? 100 : 0)
                : (int) Math.round(completedRequiredItems * 100.0 / totalRequiredItems);
        return new CourseStudentResponse(
                enrollment.getUser().getId(),
                enrollment.getUser().getUsername(),
                enrollment.getUser().getEmail(),
                enrollment.getUser().getFirstName(),
                enrollment.getUser().getLastName(),
                enrollment.getUser().getRole().name(),
                enrollment.getStatus().name(),
                enrollment.getStartedAt(),
                enrollment.getCompletedAt(),
                completedRequiredItems,
                totalRequiredItems,
                progressPercent
        );
    }
}
