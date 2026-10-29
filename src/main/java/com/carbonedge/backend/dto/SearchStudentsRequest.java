package com.carbonedge.backend.dto;

import jakarta.validation.constraints.Min;

public record SearchStudentsRequest(
        String keyword,
        @Min(1) Integer page,
        @Min(1) Integer size
) {
}
