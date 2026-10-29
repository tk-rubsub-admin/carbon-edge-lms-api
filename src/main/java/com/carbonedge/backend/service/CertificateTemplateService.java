package com.carbonedge.backend.service;

import com.carbonedge.backend.dto.CertificateTemplateElement;
import com.carbonedge.backend.dto.CertificateTemplateSettings;
import com.carbonedge.backend.exception.ForbiddenException;
import com.carbonedge.backend.exception.NotFoundException;
import com.carbonedge.backend.exception.UnauthorizedException;
import com.carbonedge.backend.model.AuthSession;
import com.carbonedge.backend.model.SystemSetting;
import com.carbonedge.backend.model.SystemSettingAudit;
import com.carbonedge.backend.model.UserRole;
import com.carbonedge.backend.repository.AuthSessionRepository;
import com.carbonedge.backend.repository.SystemSettingAuditRepository;
import com.carbonedge.backend.repository.SystemSettingRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import javax.imageio.ImageIO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
@Slf4j
public class CertificateTemplateService {
    private static final String SETTING_KEY = "certificate.template";
    private static final int MAX_IMAGE_BYTES = 10 * 1024 * 1024;
    private static final Set<String> ELEMENT_KEYS = Set.of("name", "course", "date", "qr", "certificateId", "logo", "signature");

    private final AuthSessionRepository sessions;
    private final SystemSettingRepository settings;
    private final SystemSettingAuditRepository audits;
    private final ObjectMapper objectMapper;

    @Value("${app.content-storage-path:./data/course-content}")
    private String contentStoragePath;

    @Value("${app.public-base-url:http://localhost:8086/lms-api}")
    private String publicBaseUrl;

    @Transactional(readOnly = true)
    public CertificateTemplateSettings get(String authorization) {
        requireAdmin(authorization);
        return settings.findById(SETTING_KEY)
                .map(setting -> deserialize(setting.getValue()))
                .orElseGet(this::defaults);
    }

    @Transactional
    public CertificateTemplateSettings save(String authorization, CertificateTemplateSettings requested) {
        AuthSession session = requireAdmin(authorization);
        CertificateTemplateSettings current = settings.findById(SETTING_KEY)
                .map(setting -> deserialize(setting.getValue())).orElseGet(this::defaults);
        CertificateTemplateSettings normalized = validatedWithFixedLayout(requested, current);
        try {
            String json = objectMapper.writeValueAsString(normalized);
            SystemSetting setting = settings.findById(SETTING_KEY).orElseGet(SystemSetting::new);
            String previous = setting.getValue();
            setting.setKey(SETTING_KEY);
            setting.setValue(json);
            setting.setSecret(false);
            setting.setUpdatedBy(session.getUser());
            setting.setUpdatedAt(Instant.now());
            settings.save(setting);

            SystemSettingAudit audit = new SystemSettingAudit();
            audit.setKey(SETTING_KEY);
            audit.setSecret(false);
            audit.setOldValue(previous == null ? "(ค่าเริ่มต้น)" : "อัปเดตเทมเพลตก่อนหน้า");
            audit.setNewValue("อัปเดตเทมเพลตใบประกาศนียบัตร");
            audit.setChangedBy(session.getUser());
            audit.setChangedAt(Instant.now());
            audits.save(audit);
            return normalized;
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to save certificate template", exception);
        }
    }

    @Transactional
    public String upload(String authorization, MultipartFile file, String kind) {
        AuthSession session = requireAdmin(authorization);
        if (file == null || file.isEmpty() || file.getSize() > MAX_IMAGE_BYTES) {
            throw new IllegalArgumentException("กรุณาเลือกไฟล์รูปภาพขนาดไม่เกิน 10 MB");
        }
        if (!"signature".equals(kind)) {
            throw new IllegalArgumentException("ชนิดรูปภาพไม่ถูกต้อง");
        }
        try {
            BufferedImage image = ImageIO.read(file.getInputStream());
            if (image == null || image.getWidth() > 6000 || image.getHeight() > 8500) {
                throw new IllegalArgumentException("ไฟล์ต้องเป็น PNG หรือ JPEG และมีขนาดภาพไม่เกิน 6000 × 8500 พิกเซล");
            }
            String format = "image/png".equalsIgnoreCase(file.getContentType()) ? "png" :
                    "image/jpeg".equalsIgnoreCase(file.getContentType()) ? "jpg" : null;
            if (format == null) {
                throw new IllegalArgumentException("รองรับเฉพาะไฟล์ PNG และ JPEG");
            }
            String filename = kind + "-" + UUID.randomUUID() + "." + format;
            Path directory = templateDirectory();
            Files.createDirectories(directory);
            Path target = directory.resolve(filename).normalize();
            if (!target.startsWith(directory)) throw new IllegalArgumentException("Invalid upload path");
            Files.copy(file.getInputStream(), target);
            return assetUrl(filename);
        } catch (IOException exception) {
            throw new IllegalStateException("ไม่สามารถบันทึกไฟล์รูปภาพได้", exception);
        }
    }

    public FileSystemResource asset(String filename) {
        if (filename == null || !filename.matches("(?:background|logo|signature)-[a-f0-9-]+\\.(?:png|jpg)")) {
            throw new NotFoundException("Template asset not found");
        }
        Path directory = templateDirectory();
        Path file = directory.resolve(filename).normalize();
        if (!file.startsWith(directory) || !Files.isRegularFile(file)) {
            throw new NotFoundException("Template asset not found");
        }
        return new FileSystemResource(file);
    }

    @Transactional(readOnly = true)
    public CertificateTemplateSettings getForRendering() {
        return settings.findById(SETTING_KEY)
                .map(setting -> deserialize(setting.getValue()))
                .orElseGet(this::defaults);
    }

    public BufferedImage readImage(String url) {
        if (url == null || url.isBlank()) return null;
        String prefix = "/certificate-template-assets/";
        int index = url.lastIndexOf(prefix);
        if (index < 0) return null;
        String filename = url.substring(index + prefix.length());
        try {
            return ImageIO.read(asset(filename).getInputStream());
        } catch (IOException exception) {
            log.warn("Unable to read certificate template image: filename={}, exceptionType={}",
                    filename, exception.getClass().getSimpleName());
            return null;
        }
    }

    private CertificateTemplateSettings validate(CertificateTemplateSettings value) {
        if (value == null || value.elements() == null) throw new IllegalArgumentException("Template elements are required");
        if (value.elements().size() != ELEMENT_KEYS.size()) throw new IllegalArgumentException("Template must contain all supported elements");
        Set<String> supplied = value.elements().stream().map(CertificateTemplateElement::key).collect(java.util.stream.Collectors.toSet());
        if (!supplied.equals(ELEMENT_KEYS)) throw new IllegalArgumentException("Template contains unsupported or duplicate elements");
        for (CertificateTemplateElement element : value.elements()) {
            if (!finiteBetween(element.x(), 0, 100) || !finiteBetween(element.y(), 0, 100)
                    || !finiteBetween(element.width(), 1, 100) || !finiteBetween(element.height(), 1, 100)
                    || !finiteBetween(element.fontSize(), Set.of("name", "course", "date", "certificateId").contains(element.key()) ? 8 : 0, 120)) {
                throw new IllegalArgumentException("ตำแหน่ง ขนาด หรือขนาดตัวอักษรอยู่นอกช่วงที่อนุญาต");
            }
            if (element.color() == null || !element.color().matches("#[0-9a-fA-F]{6}")) {
                throw new IllegalArgumentException("สีข้อความต้องอยู่ในรูปแบบ #RRGGBB");
            }
        }
        CertificateTemplateSettings defaults = defaults();
        return new CertificateTemplateSettings(value.backgroundUrl(), value.logoUrl(), value.signatureUrl(), value.elements(),
                defaultText(value.certificateTitle(), defaults.certificateTitle()),
                defaultText(value.recipientIntro(), defaults.recipientIntro()),
                defaultText(value.courseIntro(), defaults.courseIntro()),
                defaultText(value.achievementLine1(), defaults.achievementLine1()),
                defaultText(value.achievementLine2(), defaults.achievementLine2()),
                defaultText(value.signatoryName(), defaults.signatoryName()),
                defaultText(value.signatoryTitle(), defaults.signatoryTitle()));
    }

    private CertificateTemplateSettings validatedWithFixedLayout(
            CertificateTemplateSettings requested, CertificateTemplateSettings current) {
        if (requested == null) throw new IllegalArgumentException("Template text is required");
        CertificateTemplateSettings text = validate(new CertificateTemplateSettings(
                current.backgroundUrl(), current.logoUrl(), requested.signatureUrl(), current.elements(),
                requested.certificateTitle(), requested.recipientIntro(), requested.courseIntro(),
                requested.achievementLine1(), requested.achievementLine2(), requested.signatoryName(),
                requested.signatoryTitle()));
        boolean signatureEnabled = requested.elements() != null && requested.elements().stream()
                .anyMatch(element -> "signature".equals(element.key()) && element.enabled());
        List<CertificateTemplateElement> fixedElements = fixedElements(signatureEnabled);
        return new CertificateTemplateSettings(text.backgroundUrl(), text.logoUrl(), text.signatureUrl(), fixedElements,
                text.certificateTitle(), text.recipientIntro(), text.courseIntro(), text.achievementLine1(),
                text.achievementLine2(), text.signatoryName(), text.signatoryTitle());
    }

    private String defaultText(String value, String fallback) {
        if (value == null) return fallback;
        if (value.length() > 300) throw new IllegalArgumentException("ข้อความในเทมเพลตต้องไม่เกิน 300 ตัวอักษร");
        return value;
    }

    private boolean finiteBetween(double value, double min, double max) {
        return Double.isFinite(value) && value >= min && value <= max;
    }

    private CertificateTemplateSettings deserialize(String json) {
        try {
            CertificateTemplateSettings stored = objectMapper.readValue(json, CertificateTemplateSettings.class);
            boolean signatureEnabled = stored.elements() != null && stored.elements().stream()
                    .anyMatch(element -> "signature".equals(element.key()) && element.enabled());
            CertificateTemplateSettings normalized = validate(new CertificateTemplateSettings(
                    stored.backgroundUrl(), stored.logoUrl(), stored.signatureUrl(), fixedElements(signatureEnabled),
                    stored.certificateTitle(), stored.recipientIntro(), stored.courseIntro(),
                    stored.achievementLine1(), stored.achievementLine2(), stored.signatoryName(), stored.signatoryTitle()));
            return new CertificateTemplateSettings(normalized.backgroundUrl(), normalized.logoUrl(), normalized.signatureUrl(),
                    fixedElements(signatureEnabled), normalized.certificateTitle(), normalized.recipientIntro(),
                    normalized.courseIntro(), normalized.achievementLine1(), normalized.achievementLine2(),
                    normalized.signatoryName(), normalized.signatoryTitle());
        } catch (Exception exception) {
            log.warn("Unable to load saved certificate template; falling back to defaults: exceptionType={}",
                    exception.getClass().getSimpleName());
            return defaults();
        }
    }

    private CertificateTemplateSettings defaults() {
        return new CertificateTemplateSettings(null, null, null, fixedElements(false),
                "ใบประกาศนียบัตร", "ฉบับนี้ให้ไว้เพื่อแสดงว่า", "ได้ผ่านการอบรมและทดสอบตามหลักสูตร",
                "และผ่านการประเมินตามเกณฑ์ที่กำหนด", "ขอให้มีความมุ่งมั่นพัฒนาองค์ความรู้ เพื่อประโยชน์ต่อสิ่งแวดล้อมและสังคมสืบไป",
                "", "");
    }

    private List<CertificateTemplateElement> fixedElements(boolean signatureEnabled) {
        return List.of(
                element("name", "ชื่อผู้เรียน", 12.6, 43, 75, 7, 48, "#0B4A31", true),
                element("course", "ชื่อหลักสูตร", 12.1, 58, 76, 5, 39, "#0B4A31", true),
                element("date", "วันที่ออกใบประกาศ", 15.9, 70, 68.2, 2, 27, "#0B4A31", true),
                element("qr", "QR Code", 4.27, 88.53, 12, 8.45, 0, "#0B4A31", true),
                element("certificateId", "Certificate ID", 17.35, 90.8, 49.3, 3, 16, "#0B4A31", true),
                element("logo", "โลโก้", 35, 5, 30, 18, 0, "#0B4A31", false),
                element("signature", "ลายเซ็น", 38, 81, 24, 2.7, 0, "#0B4A31", signatureEnabled)
        );
    }

    private CertificateTemplateElement element(String key, String label, double x, double y, double width,
            double height, double fontSize, String color, boolean enabled) {
        return new CertificateTemplateElement(key, label, x, y, width, height, fontSize, color, enabled);
    }

    private AuthSession requireAdmin(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) throw new UnauthorizedException("Missing bearer token");
        AuthSession session = sessions.findByAccessToken(authorization.substring(7).trim())
                .orElseThrow(() -> new UnauthorizedException("Invalid access token"));
        if (!session.isActive()) throw new UnauthorizedException("Session has expired");
        if (session.getUser().getRole() != UserRole.ADMIN) throw new ForbiddenException("Admin access is required");
        return session;
    }

    private Path templateDirectory() {
        return Path.of(contentStoragePath).toAbsolutePath().normalize().resolve("certificate-templates").normalize();
    }

    private String assetUrl(String filename) {
        return publicBaseUrl.replaceAll("/$", "") + "/certificate-template-assets/" + filename;
    }

    public MediaType mediaType(String filename) {
        return filename != null && filename.endsWith(".jpg") ? MediaType.IMAGE_JPEG : MediaType.IMAGE_PNG;
    }
}
