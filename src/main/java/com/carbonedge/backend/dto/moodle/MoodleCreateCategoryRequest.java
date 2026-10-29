package com.carbonedge.backend.dto.moodle;

public record MoodleCreateCategoryRequest(
        String name,
        Long parent,
        String idNumber,
        String description,
        boolean visible,
        String theme
) {
}
