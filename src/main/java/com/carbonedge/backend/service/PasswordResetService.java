package com.carbonedge.backend.service;

import com.carbonedge.backend.exception.ConflictException;
import com.carbonedge.backend.exception.UnauthorizedException;
import com.carbonedge.backend.client.moodle.MoodleClient;
import com.carbonedge.backend.model.MoodleUserMapping;
import com.carbonedge.backend.model.PasswordResetToken;
import com.carbonedge.backend.model.UserAccount;
import com.carbonedge.backend.model.UserStatus;
import com.carbonedge.backend.repository.MoodleUserMappingRepository;
import com.carbonedge.backend.repository.PasswordResetTokenRepository;
import com.carbonedge.backend.repository.UserAccountRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PasswordResetService {

    private static final Duration RESET_TOKEN_TTL = Duration.ofHours(1);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final PasswordResetTokenRepository tokenRepository;
    private final UserAccountRepository userAccountRepository;
    private final MoodleUserMappingRepository moodleUserMappingRepository;
    private final PasswordEncoder passwordEncoder;
    private final MoodleClient moodleClient;
    private final ObjectProvider<EmailService> emailServiceProvider;

    @Value("${app.public-base-url:http://localhost:8086/lms-api}")
    private String publicBaseUrl;

    @Value("${app.frontend-base-url:http://localhost:4000}")
    private String frontendBaseUrl;

    @Transactional
    public Instant issueResetEmail(UserAccount user) {
        String rawToken = generateToken();
        Instant expiresAt = Instant.now().plus(RESET_TOKEN_TTL);

        PasswordResetToken token = new PasswordResetToken();
        token.setUser(user);
        token.setTokenHash(hash(rawToken));
        token.setExpiresAt(expiresAt);
        tokenRepository.save(token);

        String resetUrl = buildResetUrl(rawToken);
        resolveEmailService().sendHtmlEmail(
                user.getEmail(),
                "Reset your Carbon Edge password",
                buildTextBody(user, resetUrl, expiresAt),
                buildHtmlBody(user, resetUrl, expiresAt)
        );
        return expiresAt;
    }

    @Transactional
    public UserAccount resetPassword(String rawToken, String newPassword) {
        String tokenHash = hash(rawToken);
        PasswordResetToken token = tokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new UnauthorizedException("Invalid password reset token"));

        if (!token.isActive()) {
            throw new UnauthorizedException("Password reset token has expired or already been used");
        }

        UserAccount user = token.getUser();
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new ConflictException("Account is not active");
        }

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userAccountRepository.save(user);

        MoodleUserMapping mapping = moodleUserMappingRepository.findByUser(user)
                .orElseThrow(() -> new UnauthorizedException("User has no Moodle mapping"));
        Long moodleUserId = mapping.getMoodleUserId();
        moodleClient.updatePassword(moodleUserId, newPassword);

        token.setUsedAt(Instant.now());
        tokenRepository.save(token);

        return user;
    }

    private String buildResetUrl(String rawToken) {
        return normalizeBaseUrl(frontendBaseUrl) + "/reset-password?token=" + rawToken;
    }

    private String buildTextBody(UserAccount user, String resetUrl, Instant expiresAt) {
        return """
                Hello %s,

                You requested a password reset. Open this link to continue:
                %s

                This link expires at %s UTC.
                """.formatted(user.getFirstName(), resetUrl, expiresAt);
    }

    private String buildHtmlBody(UserAccount user, String resetUrl, Instant expiresAt) {
        return """
                <html>
                  <body>
                    <p>Hello %s,</p>
                    <p>You requested a password reset. Click below to continue:</p>
                    <p><a href="%s">Reset Password</a></p>
                    <p>This link expires at %s UTC.</p>
                  </body>
                </html>
                """.formatted(user.getFirstName(), resetUrl, expiresAt);
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
            throw new IllegalStateException("Failed to hash reset token", exception);
        }
    }

    private String normalizeBaseUrl(String baseUrl) {
        return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    private EmailService resolveEmailService() {
        return emailServiceProvider.getIfAvailable(() -> (to, subject, textBody, htmlBody) -> {
            // No email backend configured. Local/test mode can still exercise the flow.
        });
    }
}
