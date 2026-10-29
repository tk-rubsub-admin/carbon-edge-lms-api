package com.carbonedge.backend.dto;

import java.time.Instant;
import java.util.List;

public record MoodleCourseContentResponse(
        Long courseId,
        String shortName,
        String fullName,
        String displayName,
        String summary,
        Integer sectionCount,
        Integer itemCount,
        String enrollmentStatus,
        Instant courseStartedAt,
        Instant courseCompletedAt,
        List<CourseSectionResponse> sections
) {
}
