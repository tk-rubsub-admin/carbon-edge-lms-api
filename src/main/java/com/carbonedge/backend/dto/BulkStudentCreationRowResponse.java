package com.carbonedge.backend.dto;

public record BulkStudentCreationRowResponse(
        int rowNumber,
        String username,
        String status,
        String message
) {
}
