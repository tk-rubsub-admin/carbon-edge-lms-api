package com.carbonedge.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record DuplicateCourseRequest(
        @NotBlank
        @Size(max = 100)
        String shortName,
        @NotBlank
        @Size(max = 255)
        String fullName,
        @NotNull
        @Positive
        Integer categoryId,
        @Size(max = 1000)
        String summary,
        Boolean visible,
        Boolean copyTeachers,
        Boolean copyStudents
) {
}
