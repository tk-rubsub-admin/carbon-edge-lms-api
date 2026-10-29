package com.carbonedge.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import com.carbonedge.backend.validation.PasswordPolicy;

public record ResetPasswordRequest(
        @NotBlank
        String token,
        @NotBlank
        @Size(min = 8, max = 100)
        @PasswordPolicy
        String newPassword
) {
}
