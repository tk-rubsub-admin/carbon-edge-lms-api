package com.carbonedge.backend.dto;

public record CertificateTemplateElement(
        String key,
        String label,
        double x,
        double y,
        double width,
        double height,
        double fontSize,
        String color,
        boolean enabled
) {
}
