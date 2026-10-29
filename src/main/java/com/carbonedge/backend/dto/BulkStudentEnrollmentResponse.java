package com.carbonedge.backend.dto;

import java.util.List;

public record BulkStudentEnrollmentResponse(
        int totalRows,
        int enrolledCount,
        int alreadyEnrolledCount,
        int failedCount,
        List<BulkStudentEnrollmentRowResponse> results
) {
}
