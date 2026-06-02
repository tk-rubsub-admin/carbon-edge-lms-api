package com.carbonedge.backend.service;

import com.carbonedge.backend.config.MoodleIntegrationProperties;
import com.carbonedge.backend.dto.CourseLaunchConsumeResponse;
import com.carbonedge.backend.dto.CourseLaunchResponse;
import com.carbonedge.backend.exception.IntegrationException;
import com.carbonedge.backend.exception.UnauthorizedException;
import com.carbonedge.backend.launch.LaunchTokenPayload;
import com.carbonedge.backend.model.LaunchToken;
import com.carbonedge.backend.model.UserAccount;
import com.carbonedge.backend.repository.LaunchTokenRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class MoodleLaunchService {

    public static final String ISSUER = "lms-api";
    public static final String PLUGIN_SECRET_HEADER = "X-Lms-Launch-Secret";

    private final LaunchTokenRepository launchTokenRepository;
    private final MoodleIntegrationProperties properties;
    private final ObjectMapper objectMapper;

    public MoodleLaunchService(
            LaunchTokenRepository launchTokenRepository,
            MoodleIntegrationProperties properties,
            ObjectMapper objectMapper
    ) {
        this.launchTokenRepository = launchTokenRepository;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public CourseLaunchResponse issueLaunchUrl(UserAccount user, Long moodleUserId, Long courseId, String redirectPath) {
        requireSharedSecret();

        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plusSeconds(properties.getLaunchTokenTtlSeconds());
        String jti = UUID.randomUUID().toString();

        LaunchToken launchToken = new LaunchToken();
        launchToken.setJti(jti);
        launchToken.setUser(user);
        launchToken.setMoodleUserId(moodleUserId);
        launchToken.setCourseId(courseId);
        launchToken.setRedirectPath(redirectPath);
        launchToken.setExpiresAt(expiresAt);
        launchTokenRepository.save(launchToken);

        LaunchTokenPayload payload = new LaunchTokenPayload(
                ISSUER,
                user.getId(),
                moodleUserId,
                courseId,
                redirectPath,
                jti,
                issuedAt.getEpochSecond(),
                expiresAt.getEpochSecond()
        );

        String token = sign(payload);
        String launchUrl = normalizeBaseUrl(properties.getLoginBaseUrl())
                + normalizePluginPath(properties.getLaunchPluginPath())
                + "?token="
                + base64UrlEncode(token.getBytes(StandardCharsets.UTF_8));

        return new CourseLaunchResponse(courseId, launchUrl, expiresAt);
    }

    @Transactional
    public CourseLaunchConsumeResponse consume(String token, String pluginSecretHeader) {
        requireSharedSecret();
        if (!MessageDigest.isEqual(
                properties.getLaunchSharedSecret().getBytes(StandardCharsets.UTF_8),
                safeString(pluginSecretHeader).getBytes(StandardCharsets.UTF_8)
        )) {
            throw new UnauthorizedException("Invalid plugin secret");
        }

        LaunchTokenPayload payload = verify(token);
        LaunchToken launchToken = launchTokenRepository.findByJti(payload.jti())
                .orElseThrow(() -> new UnauthorizedException("Launch token not found"));

        if (!launchToken.getUser().getId().equals(payload.sub())
                || !launchToken.getMoodleUserId().equals(payload.muid())
                || !safeLong(launchToken.getCourseId()).equals(safeLong(payload.cid()))
                || !launchToken.getRedirectPath().equals(payload.redir())) {
            throw new UnauthorizedException("Launch token payload mismatch");
        }

        int updated = launchTokenRepository.consumeIfPending(payload.jti(), Instant.now(), Instant.now());
        if (updated == 0) {
            throw new UnauthorizedException("Launch token is expired or already consumed");
        }

        return new CourseLaunchConsumeResponse(
                launchToken.getMoodleUserId(),
                launchToken.getUser().getUsername(),
                launchToken.getCourseId(),
                launchToken.getRedirectPath()
        );
    }

    public LaunchTokenPayload verify(String token) {
        requireSharedSecret();

        String[] segments = token.split("\\.", 2);
        if (segments.length != 2) {
            throw new UnauthorizedException("Invalid launch token format");
        }

        byte[] expectedSignature = signBytes(segments[0]);
        byte[] actualSignature = base64UrlDecode(segments[1]);
        if (!MessageDigest.isEqual(expectedSignature, actualSignature)) {
            throw new UnauthorizedException("Invalid launch token signature");
        }

        LaunchTokenPayload payload = parsePayload(segments[0]);
        if (!ISSUER.equals(payload.iss())) {
            throw new UnauthorizedException("Invalid launch token issuer");
        }
        if (payload.exp() == null || payload.exp() < Instant.now().getEpochSecond()) {
            throw new UnauthorizedException("Launch token has expired");
        }
        if (!StringUtils.hasText(payload.redir()) || !payload.redir().startsWith("/")) {
            throw new UnauthorizedException("Invalid launch redirect path");
        }
        return payload;
    }

    private String sign(LaunchTokenPayload payload) {
        String payloadJson;
        try {
            payloadJson = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new IntegrationException("Failed to serialize launch token payload");
        }

        String payloadSegment = base64UrlEncode(payloadJson.getBytes(StandardCharsets.UTF_8));
        String signatureSegment = base64UrlEncode(signBytes(payloadSegment));
        return payloadSegment + "." + signatureSegment;
    }

    private LaunchTokenPayload parsePayload(String payloadSegment) {
        try {
            byte[] decoded = base64UrlDecode(payloadSegment);
            return objectMapper.readValue(decoded, LaunchTokenPayload.class);
        } catch (Exception exception) {
            throw new UnauthorizedException("Invalid launch token payload");
        }
    }

    private byte[] signBytes(String payloadSegment) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(
                    properties.getLaunchSharedSecret().getBytes(StandardCharsets.UTF_8),
                    "HmacSHA256"
            ));
            return mac.doFinal(payloadSegment.getBytes(StandardCharsets.UTF_8));
        } catch (Exception exception) {
            throw new IntegrationException("Failed to sign launch token");
        }
    }

    private void requireSharedSecret() {
        if (!StringUtils.hasText(properties.getLaunchSharedSecret())) {
            throw new IntegrationException("MOODLE_LAUNCH_SHARED_SECRET is required");
        }
    }

    private String normalizeBaseUrl(String baseUrl) {
        return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    private String normalizePluginPath(String pluginPath) {
        return pluginPath.startsWith("/") ? pluginPath : "/" + pluginPath;
    }

    private String base64UrlEncode(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private byte[] base64UrlDecode(String value) {
        return Base64.getUrlDecoder().decode(value);
    }

    private String safeString(String value) {
        return value == null ? "" : value;
    }

    private Long safeLong(Long value) {
        return value == null ? -1L : value;
    }
}
