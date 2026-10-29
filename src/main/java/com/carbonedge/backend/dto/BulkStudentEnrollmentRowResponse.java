package com.carbonedge.backend.dto;

public record BulkStudentEnrollmentRowResponse(
        int rowNumber,
        String username,
        String status,
        String message
) {
}
