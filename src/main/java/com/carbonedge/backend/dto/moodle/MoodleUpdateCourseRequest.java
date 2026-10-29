package com.carbonedge.backend.dto.moodle;

public record MoodleUpdateCourseRequest(
        Long id,
        String shortName,
        String fullName,
        Integer categoryId,
        String summary,
        Boolean visible
) {
}
