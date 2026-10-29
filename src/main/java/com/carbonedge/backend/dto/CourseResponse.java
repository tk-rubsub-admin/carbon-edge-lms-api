package com.carbonedge.backend.dto;

import com.carbonedge.backend.dto.moodle.MoodleCourseRecord;
import com.carbonedge.backend.model.CourseStudentEnrollment;
import java.time.Instant;

public record CourseResponse(
        Long id,
        String shortName,
        String fullName,
        String displayName,
        Integer categoryId,
        String summary,
        String enrollmentStatus,
        Instant courseStartedAt,
        Instant courseCompletedAt,
        Integer studentCount
) {
    public static CourseResponse from(MoodleCourseRecord course) {
        return new CourseResponse(
                course.id(),
                course.shortName(),
                course.fullName(),
                course.displayName(),
                course.categoryId(),
                course.summary(),
                null,
                null,
                null,
                null
        );
    }

    public CourseResponse withEnrollment(CourseStudentEnrollment enrollment) {
        return new CourseResponse(
                id,
                shortName,
                fullName,
                displayName,
                categoryId,
                summary,
                enrollment.getStatus().name(),
                enrollment.getStartedAt(),
                enrollment.getCompletedAt(),
                studentCount
        );
    }

    public CourseResponse withStudentCount(int count) {
        return new CourseResponse(id, shortName, fullName, displayName, categoryId, summary,
                enrollmentStatus, courseStartedAt, courseCompletedAt, count);
    }
}
