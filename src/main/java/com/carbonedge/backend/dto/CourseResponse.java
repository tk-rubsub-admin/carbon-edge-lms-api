package com.carbonedge.backend.dto;

import com.carbonedge.backend.dto.moodle.MoodleCourseRecord;

public record CourseResponse(
        Long id,
        String shortName,
        String fullName,
        String displayName,
        Integer categoryId,
        String summary
) {
    public static CourseResponse from(MoodleCourseRecord course) {
        return new CourseResponse(
                course.id(),
                course.shortName(),
                course.fullName(),
                course.displayName(),
                course.categoryId(),
                course.summary()
        );
    }
}
