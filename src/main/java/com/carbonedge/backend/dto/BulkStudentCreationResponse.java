package com.carbonedge.backend.dto;

import java.util.List;

public record BulkStudentCreationResponse(
        int totalRows,
        int createdCount,
        int failedCount,
        List<BulkStudentCreationRowResponse> results
) {
}
