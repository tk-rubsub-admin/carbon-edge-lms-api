package com.carbonedge.backend.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateSystemSettingRequest(@NotBlank String value) {
}
