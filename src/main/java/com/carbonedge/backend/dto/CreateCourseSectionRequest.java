package com.carbonedge.backend.dto;

import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotBlank;

public record CreateCourseSectionRequest(
        @NotBlank
        @Size(max = 255)
        String title,
        @Size(max = 2000)
        String description,
        Integer orderIndex,
        Boolean visible,
        Boolean locked
) {
}
