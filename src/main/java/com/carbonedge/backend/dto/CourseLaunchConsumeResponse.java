package com.carbonedge.backend.dto;

public record CourseLaunchConsumeResponse(
        Long moodleUserId,
        String username,
        Long courseId,
        String redirectPath
) {
}
