package com.carbonedge.backend.dto.moodle;

public record MoodleCreateUserRequest(
        String username,
        String email,
        String password,
        String firstName,
        String lastName
) {
}
