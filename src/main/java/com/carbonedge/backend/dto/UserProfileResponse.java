package com.carbonedge.backend.dto;

import com.carbonedge.backend.model.UserAccount;

public record UserProfileResponse(
        Long id,
        String username,
        String email,
        String firstName,
        String lastName,
        String title,
        String phoneNumber,
        String addressHouseNumber,
        String addressMoo,
        String addressBuilding,
        String addressAlley,
        String addressRoad,
        String addressProvince,
        String addressDistrict,
        String addressSubdistrict,
        String addressPostalCode,
        java.time.Instant pdpaConsentAt,
        java.time.Instant informationCertifiedAt,
        String status,
        String role,
        Long moodleUserId,
        boolean firstLogin,
        boolean completedProfile
) {
    public static UserProfileResponse from(UserAccount user, Long moodleUserId) {
        return new UserProfileResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.getTitle(),
                user.getPhoneNumber(),
                user.getAddressHouseNumber(),
                user.getAddressMoo(),
                user.getAddressBuilding(),
                user.getAddressAlley(),
                user.getAddressRoad(),
                user.getAddressProvince(),
                user.getAddressDistrict(),
                user.getAddressSubdistrict(),
                user.getAddressPostalCode(),
                user.getPdpaConsentAt(),
                user.getInformationCertifiedAt(),
                user.getStatus().name(),
                user.getRole().name(),
                moodleUserId,
                user.isFirstLogin(),
                user.isCompletedProfile()
        );
    }
}
