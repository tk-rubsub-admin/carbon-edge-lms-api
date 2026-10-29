package com.carbonedge.backend.dto.moodle;

public record MoodleCategoryRecord(
        Long id,
        String name,
        String idNumber,
        String description,
        Long parent,
        Integer sortOrder,
        Integer courseCount,
        Boolean visible,
        Boolean visibleOld,
        Long timeModified,
        Integer depth,
        String path,
        String theme
) {
}
