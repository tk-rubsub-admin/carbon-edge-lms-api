package com.carbonedge.backend.service;

import com.carbonedge.backend.exception.ConflictException;
import com.carbonedge.backend.exception.UnauthorizedException;
import com.carbonedge.backend.model.EmailVerificationToken;
import com.carbonedge.backend.model.UserAccount;
import com.carbonedge.backend.model.UserStatus;
import com.carbonedge.backend.repository.EmailVerificationTokenRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailVerificationService {

    private static final Duration VERIFICATION_TOKEN_TTL = Duration.ofHours(24);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final EmailVerificationTokenRepository tokenRepository;
    private final ObjectProvider<EmailService> emailServiceProvider;

    @Value("${app.public-base-url:http://localhost:8086/lms-api}")
    private String publicBaseUrl;

    @Transactional
    public Instant issueVerificationEmail(UserAccount user) {
        if (!StringUtils.hasText(user.getEmail())) {
            throw new IllegalArgumentException("User email is required");
        }
        String rawToken = generateToken();
        Instant expiresAt = Instant.now().plus(VERIFICATION_TOKEN_TTL);

        EmailVerificationToken token = new EmailVerificationToken();
        token.setUser(user);
        token.setTokenHash(hash(rawToken));
        token.setExpiresAt(expiresAt);
        tokenRepository.save(token);

        String verificationUrl = buildVerificationUrl(rawToken);
        resolveEmailService().sendHtmlEmail(
                user.getEmail(),
                "Verify your Carbon Edge account",
                buildTextBody(user, verificationUrl, expiresAt),
                buildHtmlBody(user, verificationUrl, expiresAt)
        );
        return expiresAt;
    }

    @Transactional
    public Instant resendVerificationEmail(UserAccount user) {
        return issueVerificationEmail(user);
    }

    @Transactional
    public UserAccount verifyToken(String rawToken) {
        String tokenHash = hash(rawToken);
        EmailVerificationToken token = tokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new UnauthorizedException("Invalid verification token"));

        if (!token.isActive()) {
            throw new UnauthorizedException("Verification token has expired or already been used");
        }

        UserAccount user = token.getUser();
        if (user.getStatus() == UserStatus.ACTIVE) {
            throw new ConflictException("Account is already verified");
        }

        user.setStatus(UserStatus.ACTIVE);
        token.setUsedAt(Instant.now());
        tokenRepository.save(token);

        return user;
    }

    private String buildVerificationUrl(String rawToken) {
        return normalizeBaseUrl(publicBaseUrl) + "/auth/verify-email?token=" + rawToken;
    }

    private String buildTextBody(UserAccount user, String verificationUrl, Instant expiresAt) {
        return """
                Hello %s,

                Verify your Carbon Edge account by opening this link:
                %s

                This link expires at %s UTC.
                """.formatted(user.getFirstName(), verificationUrl, expiresAt);
    }

    private String buildHtmlBody(UserAccount user, String verificationUrl, Instant expiresAt) {
        return """
                <html>
                  <body>
                    <p>Hello %s,</p>
                    <p>Verify your Carbon Edge account by clicking the link below:</p>
                    <p><a href="%s">Verify Email</a></p>
                    <p>This link expires at %s UTC.</p>
                  </body>
                </html>
                """.formatted(user.getFirstName(), verificationUrl, expiresAt);
    }

    private String generateToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to hash verification token", exception);
        }
    }

    private String normalizeBaseUrl(String baseUrl) {
        return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    private EmailService resolveEmailService() {
        return emailServiceProvider.getIfAvailable(() -> (to, subject, textBody, htmlBody) -> {
            // No email backend configured. Registration still succeeds in local/test mode.
            log.warn("No email backend configured. Registration still succeeds in local/test mode.");
        });
    }
}
