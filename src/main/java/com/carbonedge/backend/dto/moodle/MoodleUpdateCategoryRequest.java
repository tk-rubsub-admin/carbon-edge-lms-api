package com.carbonedge.backend.dto.moodle;

public record MoodleUpdateCategoryRequest(
        Long id,
        String name,
        Long parent,
        String idNumber,
        String description,
        Boolean visible,
        String theme
) {
}
