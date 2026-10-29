package com.carbonedge.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record CreateCategoryRequest(
        @NotBlank
        @Size(max = 255)
        String name,
        @NotNull
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
