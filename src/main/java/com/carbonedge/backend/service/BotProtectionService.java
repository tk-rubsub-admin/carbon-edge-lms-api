package com.carbonedge.backend.service;

import com.carbonedge.backend.dto.RegisterRequest;
import com.carbonedge.backend.exception.ForbiddenException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/** Lightweight server-side bot protection for installations without a CAPTCHA provider. */
@Service
public class BotProtectionService {

    @Value("${app.bot-protection.enabled:true}")
    private boolean enabled;

    @Value("${app.bot-protection.minimum-fill-seconds:2}")
    private long minimumFillSeconds;

    public void validate(RegisterRequest request) {
        if (!enabled) return;
        if (StringUtils.hasText(request.website())) {
            throw new ForbiddenException("Registration could not be verified");
        }
        if (request.formStartedAt() == null
                || System.currentTimeMillis() - request.formStartedAt() < minimumFillSeconds * 1000L) {
            throw new ForbiddenException("Registration could not be verified");
        }
    }
}
