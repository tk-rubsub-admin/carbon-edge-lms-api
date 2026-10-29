package com.carbonedge.backend.dto;

import com.carbonedge.backend.model.CourseTemplateType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CreateCourseTemplateRequest(
        @NotBlank
        @Size(max = 100)
        String code,
        @NotBlank
        @Size(max = 255)
        String name,
        @Size(max = 2000)
        String description,
        @NotNull
        CourseTemplateType templateType,
        @NotNull
        @Positive
        Long sourceCourseId,
        Integer categoryIdDefault,
        Boolean visibleDefault,
        Boolean active
) {
}
