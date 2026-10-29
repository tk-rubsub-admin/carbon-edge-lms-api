package com.carbonedge.backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import com.carbonedge.backend.validation.PasswordPolicy;

public record RegisterRequest(
        @NotBlank
        @Size(min = 3, max = 50)
        @Pattern(regexp = "^[a-zA-Z0-9._-]+$")
        String username,
        @Email
        String email,
        @NotBlank
        @Size(min = 8, max = 100)
        @PasswordPolicy
        String password,
        @Size(max = 100)
        String firstName,
        @Size(max = 100)
        String lastName,
        @Size(max = 20)
        String title,
        @Pattern(regexp = "^$|^0[0-9]{9}$", message = "Phone number must contain 10 digits and start with 0")
        String phoneNumber,
        @Size(max = 50)
        String addressHouseNumber,
        @Size(max = 50)
        String addressMoo,
        @Size(max = 255)
        String addressBuilding,
        @Size(max = 255)
        String addressAlley,
        @Size(max = 255)
        String addressRoad,
        @Size(max = 100)
        String addressProvince,
        @Size(max = 100)
        String addressDistrict,
        @Size(max = 100)
        String addressSubdistrict,
        @Pattern(regexp = "^$|^[0-9]{5}$", message = "Postal code must contain 5 digits")
        String addressPostalCode,
        Boolean pdpaConsent,
        Boolean informationCertified,
        @Size(max = 200)
        String website,
        Long formStartedAt
) {
}
