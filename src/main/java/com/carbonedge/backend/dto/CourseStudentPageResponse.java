package com.carbonedge.backend.dto;

import java.util.List;

public record CourseStudentPageResponse(
        List<CourseStudentResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
}
