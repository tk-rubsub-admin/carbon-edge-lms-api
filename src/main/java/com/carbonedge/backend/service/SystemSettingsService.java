package com.carbonedge.backend.service;

import com.carbonedge.backend.client.moodle.MoodleClient;
import com.carbonedge.backend.config.MoodleIntegrationProperties;
import com.carbonedge.backend.dto.SystemSettingAuditResponse;
import com.carbonedge.backend.dto.SystemSettingResponse;
import com.carbonedge.backend.exception.ForbiddenException;
import com.carbonedge.backend.exception.IntegrationException;
import com.carbonedge.backend.exception.NotFoundException;
import com.carbonedge.backend.exception.UnauthorizedException;
import com.carbonedge.backend.model.AuthSession;
import com.carbonedge.backend.model.SystemSetting;
import com.carbonedge.backend.model.SystemSettingAudit;
import com.carbonedge.backend.model.UserAccount;
import com.carbonedge.backend.model.UserRole;
import com.carbonedge.backend.repository.AuthSessionRepository;
import com.carbonedge.backend.repository.SystemSettingAuditRepository;
import com.carbonedge.backend.repository.SystemSettingRepository;
import jakarta.annotation.PostConstruct;
import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.mail.MailProperties;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

@Service
@RequiredArgsConstructor
public class SystemSettingsService {
    public static final String MOODLE_BASE_URL = "moodle.base-url";
    public static final String MOODLE_LOGIN_BASE_URL = "moodle.login-base-url";
    public static final String MOODLE_SERVICE_TOKEN = "moodle.service-token";
    public static final String MOODLE_LAUNCH_SECRET = "moodle.launch-shared-secret";
    public static final String MOODLE_LAUNCH_TTL = "moodle.launch-token-ttl-seconds";
    public static final String MAX_UPLOAD_SIZE_MB = "learning.max-upload-size-mb";
    public static final String MAIL_HOST = "mail.host";
    public static final String MAIL_PORT = "mail.port";
    public static final String MAIL_USERNAME = "mail.username";
    public static final String MAIL_PASSWORD = "mail.password";
    private static final int DEFAULT_MAX_UPLOAD_SIZE_MB = 1024;
    private static final int MAX_ALLOWED_UPLOAD_SIZE_MB = 1024;
    private static final Set<String> SUPPORTED_KEYS = Set.of(
            MOODLE_BASE_URL, MOODLE_LOGIN_BASE_URL, MOODLE_SERVICE_TOKEN,
            MOODLE_LAUNCH_SECRET, MOODLE_LAUNCH_TTL,
            MAX_UPLOAD_SIZE_MB, MAIL_HOST, MAIL_PORT, MAIL_USERNAME, MAIL_PASSWORD
    );
    private static final Set<String> SECRET_KEYS = Set.of(MOODLE_SERVICE_TOKEN, MOODLE_LAUNCH_SECRET, MAIL_PASSWORD);
    private static final String REDACTED = "********";
    private static final String ENCRYPTED_PREFIX = "enc:v1:";

    private final SystemSettingRepository settingRepository;
    private final SystemSettingAuditRepository auditRepository;
    private final AuthSessionRepository authSessionRepository;
    private final MoodleIntegrationProperties moodleProperties;
    private final MoodleClient moodleClient;
    private final JavaMailSender mailSender;
    private final MailProperties mailProperties;

    @Value("${app.system-settings-encryption-key:}")
    private String encryptionKey;

    @PostConstruct
    @Transactional
    void applyPersistedSettings() {
        settingRepository.findAll().forEach(setting -> apply(setting.getKey(), readableValue(setting)));
    }

    @Transactional(readOnly = true)
    public List<SystemSettingResponse> getSettings(String authorizationHeader) {
        requireAdmin(authorizationHeader);
        Map<String, SystemSetting> persisted = settingRepository.findAll().stream()
                .collect(java.util.stream.Collectors.toMap(SystemSetting::getKey, item -> item));
        return List.of(MOODLE_BASE_URL, MOODLE_LOGIN_BASE_URL, MOODLE_SERVICE_TOKEN, MOODLE_LAUNCH_SECRET,
                        MOODLE_LAUNCH_TTL, MAX_UPLOAD_SIZE_MB, MAIL_HOST, MAIL_PORT, MAIL_USERNAME, MAIL_PASSWORD)
                .stream()
                .map(key -> toResponse(key, persisted.get(key)))
                .toList();
    }

    @Transactional
    public SystemSettingResponse update(String authorizationHeader, String key, String value) {
        AuthSession session = requireAdmin(authorizationHeader);
        if (!SUPPORTED_KEYS.contains(key)) {
            throw new NotFoundException("Unsupported system setting");
        }
        String normalized = normalize(key, value);
        SystemSetting setting = settingRepository.findById(key).orElseGet(SystemSetting::new);
        String oldValue = setting.getValue();
        boolean secret = SECRET_KEYS.contains(key);
        setting.setKey(key);
        setting.setValue(secret ? encrypt(normalized) : normalized);
        setting.setSecret(secret);
        setting.setUpdatedBy(session.getUser());
        setting.setUpdatedAt(Instant.now());
        settingRepository.save(setting);
        apply(key, normalized);

        SystemSettingAudit audit = new SystemSettingAudit();
        audit.setKey(key);
        audit.setSecret(secret);
        audit.setOldValue(secret ? REDACTED : oldValue);
        audit.setNewValue(secret ? REDACTED : normalized);
        audit.setChangedBy(session.getUser());
        audit.setChangedAt(Instant.now());
        auditRepository.save(audit);
        return toResponse(key, setting);
    }

    @Transactional(readOnly = true)
    public List<SystemSettingAuditResponse> getAudit(String authorizationHeader) {
        requireAdmin(authorizationHeader);
        return auditRepository.findTop50ByOrderByChangedAtDesc().stream()
                .map(audit -> new SystemSettingAuditResponse(
                        audit.getId(), audit.getKey(), audit.getOldValue(), audit.getNewValue(), audit.isSecret(),
                        audit.getChangedAt(), audit.getChangedBy() == null ? null : audit.getChangedBy().getUsername()
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public String testMoodle(String authorizationHeader) {
        requireAdmin(authorizationHeader);
        return moodleClient.getSiteInfo();
    }

    @Transactional(readOnly = true)
    public void testMail(String authorizationHeader) {
        requireAdmin(authorizationHeader);
        if (!(mailSender instanceof JavaMailSenderImpl sender)) {
            throw new IllegalStateException("Configured mail sender does not support connection testing");
        }
        try {
            sender.testConnection();
        } catch (Exception exception) {
            throw new IntegrationException("Failed to connect to SMTP server: " + exception.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public int getMaxUploadSizeMb() {
        return settingRepository.findById(MAX_UPLOAD_SIZE_MB)
                .map(SystemSetting::getValue)
                .map(Integer::parseInt)
                .orElse(DEFAULT_MAX_UPLOAD_SIZE_MB);
    }

    private SystemSettingResponse toResponse(String key, SystemSetting setting) {
        boolean secret = SECRET_KEYS.contains(key);
        String effective = setting == null
                ? currentValue(key)
                : (secret ? "configured" : setting.getValue());
        return new SystemSettingResponse(
                key,
                secret ? "" : effective,
                secret,
                StringUtils.hasText(effective),
                setting == null ? null : setting.getUpdatedAt(),
                setting == null || setting.getUpdatedBy() == null ? null : setting.getUpdatedBy().getUsername()
        );
    }

    private AuthSession requireAdmin(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            throw new UnauthorizedException("Missing bearer token");
        }
        AuthSession session = authSessionRepository.findByAccessToken(authorizationHeader.substring(7).trim())
                .orElseThrow(() -> new UnauthorizedException("Invalid access token"));
        if (!session.isActive()) {
            throw new UnauthorizedException("Session has expired");
        }
        if (session.getUser().getRole() != UserRole.ADMIN) {
            throw new ForbiddenException("Admin access is required");
        }
        return session;
    }

    private String normalize(String key, String value) {
        String result = value == null ? "" : value.trim();
        if (!StringUtils.hasText(result)) {
            throw new IllegalArgumentException("Setting value is required");
        }
        if (key.endsWith("base-url")) {
            if (!result.startsWith("http://") && !result.startsWith("https://")) {
                throw new IllegalArgumentException("URL must start with http:// or https://");
            }
            return result.replaceAll("/+$", "");
        }
        if (MOODLE_LAUNCH_TTL.equals(key)) {
            try {
                long seconds = Long.parseLong(result);
                if (seconds < 30 || seconds > 3600) throw new NumberFormatException();
                return String.valueOf(seconds);
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException("Launch token TTL must be between 30 and 3600 seconds");
            }
        }

        if (MAX_UPLOAD_SIZE_MB.equals(key)) {
            try {
                int maxUploadSizeMb = Integer.parseInt(result);
                if (maxUploadSizeMb < 1 || maxUploadSizeMb > MAX_ALLOWED_UPLOAD_SIZE_MB) {
                    throw new NumberFormatException();
                }
                return String.valueOf(maxUploadSizeMb);
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException("Maximum upload size must be between 1 and 1024 MB");
            }
        }

        if (MAIL_PORT.equals(key)) {
            try {
                int port = Integer.parseInt(result);
                if (port < 1 || port > 65535) throw new NumberFormatException();
                return String.valueOf(port);
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException("Mail port must be between 1 and 65535");
            }
        }
        return result;
    }

    private void apply(String key, String value) {
        switch (key) {
            case MOODLE_BASE_URL -> moodleProperties.setBaseUrl(value);
            case MOODLE_LOGIN_BASE_URL -> moodleProperties.setLoginBaseUrl(value);
            case MOODLE_SERVICE_TOKEN -> moodleProperties.setServiceToken(value);
            case MOODLE_LAUNCH_SECRET -> moodleProperties.setLaunchSharedSecret(value);
            case MOODLE_LAUNCH_TTL -> moodleProperties.setLaunchTokenTtlSeconds(Long.parseLong(value));
            case MAIL_HOST -> updateMailSender(sender -> sender.setHost(value));
            case MAIL_PORT -> updateMailSender(sender -> sender.setPort(Integer.parseInt(value)));
            case MAIL_USERNAME -> updateMailSender(sender -> sender.setUsername(value));
            case MAIL_PASSWORD -> updateMailSender(sender -> sender.setPassword(value));
            default -> { }
        }
    }

    private void updateMailSender(java.util.function.Consumer<JavaMailSenderImpl> update) {
        if (!(mailSender instanceof JavaMailSenderImpl sender)) {
            throw new IllegalStateException("Configured mail sender cannot be updated at runtime");
        }
        update.accept(sender);
    }

    private String readableValue(SystemSetting setting) {
        if (!setting.isSecret()) {
            return setting.getValue();
        }
        return decrypt(setting.getValue());
    }

    private String encrypt(String value) {
        try {
            byte[] nonce = new byte[12];
            new SecureRandom().nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, encryptionKey(), new GCMParameterSpec(128, nonce));
            byte[] ciphertext = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
            return ENCRYPTED_PREFIX + Base64.getEncoder().encodeToString(nonce) + ":" + Base64.getEncoder().encodeToString(ciphertext);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to encrypt system setting", exception);
        }
    }

    private String decrypt(String value) {
        if (!value.startsWith(ENCRYPTED_PREFIX)) {
            throw new IllegalStateException("Secret system setting is not encrypted; replace it after configuring APP_SYSTEM_SETTINGS_ENCRYPTION_KEY");
        }
        try {
            String[] segments = value.substring(ENCRYPTED_PREFIX.length()).split(":", 2);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, encryptionKey(), new GCMParameterSpec(128, Base64.getDecoder().decode(segments[0])));
            return new String(cipher.doFinal(Base64.getDecoder().decode(segments[1])), StandardCharsets.UTF_8);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to decrypt system setting", exception);
        }
    }

    private SecretKeySpec encryptionKey() {
        try {
            byte[] key = Base64.getDecoder().decode(encryptionKey);
            if (key.length != 16 && key.length != 24 && key.length != 32) {
                throw new IllegalArgumentException();
            }
            return new SecretKeySpec(key, "AES");
        } catch (Exception exception) {
            throw new IllegalStateException("APP_SYSTEM_SETTINGS_ENCRYPTION_KEY must be a Base64-encoded 16, 24, or 32-byte AES key");
        }
    }

    private String currentValue(String key) {
        return switch (key) {
            case MOODLE_BASE_URL -> moodleProperties.getBaseUrl();
            case MOODLE_LOGIN_BASE_URL -> moodleProperties.getLoginBaseUrl();
            case MOODLE_SERVICE_TOKEN -> moodleProperties.getServiceToken();
            case MOODLE_LAUNCH_SECRET -> moodleProperties.getLaunchSharedSecret();
            case MOODLE_LAUNCH_TTL -> String.valueOf(moodleProperties.getLaunchTokenTtlSeconds());
            case MAX_UPLOAD_SIZE_MB -> String.valueOf(DEFAULT_MAX_UPLOAD_SIZE_MB);
            case MAIL_HOST -> mailProperties.getHost();
            case MAIL_PORT -> String.valueOf(mailProperties.getPort());
            case MAIL_USERNAME -> mailProperties.getUsername();
            case MAIL_PASSWORD -> mailProperties.getPassword();
            default -> "";
        };
    }
}
