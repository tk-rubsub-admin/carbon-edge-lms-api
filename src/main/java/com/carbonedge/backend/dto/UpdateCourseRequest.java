package com.carbonedge.backend.dto;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record UpdateCourseRequest(
        @Size(max = 100)
        String shortName,
        @Size(max = 255)
        String fullName,
        @Positive
        Integer categoryId,
        @Size(max = 1000)
        String summary,
        Boolean visible
) {
}
