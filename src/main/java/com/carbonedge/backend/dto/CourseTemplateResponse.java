package com.carbonedge.backend.dto;

import com.carbonedge.backend.model.CourseTemplate;
import com.carbonedge.backend.model.CourseTemplateType;
import java.time.Instant;

public record CourseTemplateResponse(
        Long id,
        String code,
        String name,
        String description,
        CourseTemplateType templateType,
        Long sourceCourseId,
        Integer categoryIdDefault,
        Boolean visibleDefault,
        Boolean active,
        Instant createdAt,
        Instant updatedAt
) {
    public static CourseTemplateResponse from(CourseTemplate template) {
        return new CourseTemplateResponse(
                template.getId(),
                template.getCode(),
                template.getName(),
                template.getDescription(),
                template.getTemplateType(),
                template.getSourceCourseId(),
                template.getCategoryIdDefault(),
                template.isVisibleDefault(),
                template.isActive(),
                template.getCreatedAt(),
                template.getUpdatedAt()
        );
    }
}
