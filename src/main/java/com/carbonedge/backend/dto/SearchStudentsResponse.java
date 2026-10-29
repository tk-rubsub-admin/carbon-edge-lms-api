package com.carbonedge.backend.dto;

import java.util.List;

public record SearchStudentsResponse(
        List<UserProfileResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
}
