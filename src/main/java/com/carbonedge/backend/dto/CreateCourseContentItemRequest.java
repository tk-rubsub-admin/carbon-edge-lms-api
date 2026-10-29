package com.carbonedge.backend.dto;

import com.carbonedge.backend.model.CourseContentType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateCourseContentItemRequest(
        @NotNull
        CourseContentType type,
        @NotBlank
        @Size(max = 255)
        String title,
        @Size(max = 2000)
        String description,
        Integer orderIndex,
        Boolean required,
        Boolean visible,
        Integer durationSeconds,
        @Size(max = 1000)
        String sourceUrl,
        @Size(max = 4000)
        String payloadJson,
        Boolean published
) {
}
