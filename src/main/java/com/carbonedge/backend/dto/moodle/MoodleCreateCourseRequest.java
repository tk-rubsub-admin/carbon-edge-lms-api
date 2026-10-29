package com.carbonedge.backend.dto.moodle;

public record MoodleCreateCourseRequest(
        String shortName,
        String fullName,
        Integer categoryId,
        String summary,
        boolean visible
) {
}
