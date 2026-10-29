package com.carbonedge.backend.dto;

import jakarta.validation.constraints.Size;

public record UpdateCourseSectionRequest(
        @Size(max = 255)
        String title,
        @Size(max = 2000)
        String description,
        Integer orderIndex,
        Boolean visible,
        Boolean locked
) {
}
