package com.carbonedge.backend.dto;

import com.carbonedge.backend.model.CourseTeacherAssignment;

public record CourseTeacherResponse(
        Long userId,
        String username,
        String email,
        String firstName,
        String lastName,
        String role
) {
    public static CourseTeacherResponse from(CourseTeacherAssignment assignment) {
        return new CourseTeacherResponse(
                assignment.getUser().getId(),
                assignment.getUser().getUsername(),
                assignment.getUser().getEmail(),
                assignment.getUser().getFirstName(),
                assignment.getUser().getLastName(),
                assignment.getUser().getRole().name()
        );
    }
}
