package com.carbonedge.backend.dto;

import jakarta.validation.constraints.NotBlank;

public record CourseLaunchConsumeRequest(
        @NotBlank String token
) {
}
