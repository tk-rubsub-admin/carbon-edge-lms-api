package com.carbonedge.backend.dto;

import jakarta.validation.constraints.NotNull;

public record MoodleContentProgressRequest(
        @NotNull Long moodleUserId,
        Integer currentPage,
        Integer totalPages,
        Long videoPositionMs,
        Long videoDurationMs
) { }
