package com.carbonedge.backend.dto;

import com.carbonedge.backend.dto.moodle.MoodleCategoryRecord;

public record CategoryResponse(
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
    public static CategoryResponse from(MoodleCategoryRecord category) {
        return new CategoryResponse(
                category.id(),
                category.name(),
                category.idNumber(),
                category.description(),
                category.parent(),
                category.sortOrder(),
                category.courseCount(),
                category.visible(),
                category.visibleOld(),
                category.timeModified(),
                category.depth(),
                category.path(),
                category.theme()
        );
    }
}
