package com.carbonedge.backend.dto.moodle;

public record MoodleUserRecord(
        Long id,
        String username,
        String email
) {
}
