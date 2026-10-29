package com.carbonedge.backend.dto;

import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record UpdateCategoryRequest(
        @Size(max = 255)
        String name,
        @PositiveOrZero
        Integer parent,
        @Size(max = 100)
        String idNumber,
        @Size(max = 1000)
        String description,
        Boolean visible,
        @Size(max = 50)
        String theme
) {
}
