package com.carbonedge.backend.dto.moodle;

public record MoodleUpdateUserRequest(
        Long id,
        String email,
        String firstName,
        String lastName
) {
}
