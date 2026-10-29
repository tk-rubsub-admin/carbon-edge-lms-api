package com.carbonedge.backend.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record ReorderCourseContentItemRequest(
        @NotNull
        @Min(1)
        Integer orderIndex
) {
}
