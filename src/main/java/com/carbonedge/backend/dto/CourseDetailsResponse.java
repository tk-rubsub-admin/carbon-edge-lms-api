package com.carbonedge.backend.dto;

import java.util.List;

public record CourseDetailsResponse(
        Long id,
        String shortName,
        String fullName,
        String displayName,
        Integer categoryId,
        String summary,
        List<CourseTeacherResponse> teachers,
        Integer studentCount,
        Integer sectionCount,
        Integer itemCount,
        List<CourseSectionResponse> sections
) {
}
