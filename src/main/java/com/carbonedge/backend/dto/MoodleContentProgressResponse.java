package com.carbonedge.backend.dto;

import java.time.Instant;

public record MoodleContentProgressResponse(
        Long courseId,
        Long contentItemId,
        Instant startedAt,
        Instant completedAt,
        Integer currentPage,
        Integer totalPages,
        Long videoPositionMs,
        Long videoDurationMs
) { }
