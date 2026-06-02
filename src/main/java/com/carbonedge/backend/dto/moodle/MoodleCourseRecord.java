package com.carbonedge.backend.dto.moodle;

public record MoodleCourseRecord(
        Long id,
        String shortName,
        String fullName,
        String displayName,
        Integer categoryId,
        String summary
) {
}
