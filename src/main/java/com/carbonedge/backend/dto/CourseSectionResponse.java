package com.carbonedge.backend.dto;

import com.carbonedge.backend.model.CourseSection;
import java.util.List;

public record CourseSectionResponse(
        Long id,
        Long courseId,
        String title,
        String description,
        Integer orderIndex,
        Boolean visible,
        Boolean locked,
        List<CourseContentItemResponse> items
) {
    public static CourseSectionResponse from(CourseSection section, List<CourseContentItemResponse> items) {
        return new CourseSectionResponse(
                section.getId(),
                section.getCourseId(),
                section.getTitle(),
                section.getDescription(),
                section.getOrderIndex(),
                section.isVisible(),
                section.isLocked(),
                items
        );
    }
}
