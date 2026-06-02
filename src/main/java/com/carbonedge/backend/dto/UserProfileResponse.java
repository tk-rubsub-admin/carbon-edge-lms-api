package com.carbonedge.backend.dto;

import com.carbonedge.backend.model.UserAccount;

public record UserProfileResponse(
        Long id,
        String username,
        String email,
        String firstName,
        String lastName,
        String status,
        Long moodleUserId
) {
    public static UserProfileResponse from(UserAccount user, Long moodleUserId) {
        return new UserProfileResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.getStatus().name(),
                moodleUserId
        );
    }
}
