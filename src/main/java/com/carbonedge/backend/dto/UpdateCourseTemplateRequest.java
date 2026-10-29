package com.carbonedge.backend.dto;

import jakarta.validation.constraints.Size;

public record UpdateCourseTemplateRequest(
        @Size(max = 100)
        String code,
        @Size(max = 255)
        String name,
        @Size(max = 2000)
        String description,
        Integer categoryIdDefault,
        Boolean visibleDefault,
        Boolean active
) {
}
