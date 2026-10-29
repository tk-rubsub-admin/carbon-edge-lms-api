package com.carbonedge.backend.dto;

import com.carbonedge.backend.model.CourseContentItem;
import com.carbonedge.backend.model.CourseContentType;
import java.time.Instant;

public record CourseContentItemResponse(
        Long id,
        Long sectionId,
        String type,
        String renderKind,
        String title,
        String description,
        Integer orderIndex,
        Boolean required,
        Boolean visible,
        Integer durationSeconds,
        String sourceUrl,
        String payloadJson,
        Instant publishedAt,
        Boolean completed,
        Boolean locked
) {
    public static CourseContentItemResponse from(CourseContentItem item) {
        return from(item, null, null);
    }

    public static CourseContentItemResponse from(CourseContentItem item, Boolean completed) {
        return from(item, completed, null);
    }

    public static CourseContentItemResponse from(CourseContentItem item, Boolean completed, Boolean locked) {
        return new CourseContentItemResponse(
                item.getId(),
                item.getSection().getId(),
                item.getType().name(),
                resolveRenderKind(item.getType()),
                item.getTitle(),
                item.getDescription(),
                item.getOrderIndex(),
                item.isRequired(),
                item.isVisible(),
                item.getDurationSeconds(),
                item.getSourceUrl(),
                item.getPayloadJson(),
                item.getPublishedAt(),
                completed,
                locked
        );
    }

    private static String resolveRenderKind(CourseContentType type) {
        if (type == null) {
            return "unknown";
        }

        return switch (type) {
            case VIDEO -> "video";
            case PDF, FILE -> "file";
            case LINK -> "link";
            case TEXT -> "text";
            case QUIZ -> "quiz";
            case ASSIGNMENT -> "assignment";
            case SCORM -> "scorm";
        };
    }
}
