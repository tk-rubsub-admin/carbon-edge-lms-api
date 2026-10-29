package com.carbonedge.backend.dto;

import java.util.List;

public record CertificateTemplateSettings(
        String backgroundUrl,
        String logoUrl,
        String signatureUrl,
        List<CertificateTemplateElement> elements,
        String certificateTitle,
        String recipientIntro,
        String courseIntro,
        String achievementLine1,
        String achievementLine2,
        String signatoryName,
        String signatoryTitle
) {
}
