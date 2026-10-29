package com.carbonedge.backend.service;

import com.carbonedge.backend.client.moodle.MoodleClient;
import com.carbonedge.backend.dto.moodle.MoodleCourseRecord;
import com.carbonedge.backend.exception.ForbiddenException;
import com.carbonedge.backend.exception.NotFoundException;
import com.carbonedge.backend.model.CourseCertificate;
import com.carbonedge.backend.model.CourseStudentEnrollment;
import com.carbonedge.backend.model.UserAccount;
import com.carbonedge.backend.repository.CourseCertificateRepository;
import com.carbonedge.backend.repository.SatisfactionResponseRepository;
import com.carbonedge.backend.repository.SatisfactionSurveyRepository;
import com.carbonedge.backend.model.SatisfactionSurveyStatus;
import com.carbonedge.backend.repository.UserAccountRepository;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import javax.imageio.ImageIO;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CertificateService {

    static {
        System.setProperty("java.awt.headless", "true");
    }

    private static final String TEMPLATE_PATH = "certificates/carbonedge-certificate-background-clean.png";
    private static final String REGULAR_FONT_PATH = "fonts/Sarabun-Regular.ttf";
    private static final String BOLD_FONT_PATH = "fonts/Sarabun-Bold.ttf";
    private static final int TEMPLATE_WIDTH = 1055;
    private static final int TEMPLATE_HEIGHT = 1491;
    private static final Color BRAND_GREEN = new Color(11, 74, 49);
    private static final ZoneId BANGKOK_ZONE = ZoneId.of("Asia/Bangkok");
    private static final String[] THAI_MONTHS = {
            "มกราคม", "กุมภาพันธ์", "มีนาคม", "เมษายน", "พฤษภาคม", "มิถุนายน",
            "กรกฎาคม", "สิงหาคม", "กันยายน", "ตุลาคม", "พฤศจิกายน", "ธันวาคม"
    };

    private final AuthService authService;
    private final UserAccountRepository users;
    private final CourseCertificateRepository certificates;
    private final CourseEnrollmentProgressService courseEnrollmentProgressService;
    private final MoodleClient moodleClient;
    private final CertificateTemplateService templateService;
    private final SatisfactionSurveyRepository satisfactionSurveys;
    private final SatisfactionResponseRepository satisfactionResponses;
    private final SatisfactionSurveyService satisfactionSurveyService;

    @Value("${app.public-base-url}")
    private String publicBaseUrl;

    @Value("${app.frontend-base-url}")
    private String frontendBaseUrl;

    @Transactional
    public Map<String, Object> issueMine(String auth, Long courseId) {
        UserAccount user = users.findById(authService.currentUser(auth).id()).orElseThrow();
        satisfactionSurveyService.ensureDefaultSurvey(courseId);
        CourseStudentEnrollment enrollment = courseEnrollmentProgressService.refreshCompletion(user, courseId)
                .orElseThrow(() -> new ForbiddenException("User is not enrolled in this course"));
        if (enrollment.getCompletedAt() == null) {
            throw new ForbiddenException("Complete all required content before requesting a certificate");
        }
        var publishedSurveys = satisfactionSurveys.findAllByCourseIdAndStatus(courseId, SatisfactionSurveyStatus.PUBLISHED);
        boolean hasSubmittedSurvey = satisfactionSurveys.findAllByCourseId(courseId).stream()
                .anyMatch(survey -> satisfactionResponses.existsBySurveyIdAndUserId(survey.getId(), user.getId()));
        if (!hasSubmittedSurvey || publishedSurveys.stream()
                .anyMatch(survey -> !satisfactionResponses.existsBySurveyIdAndUserId(survey.getId(), user.getId()))) {
            throw new ForbiddenException("Complete all course evaluations before requesting a certificate");
        }

        CourseCertificate certificate = certificates.findByUserIdAndCourseId(user.getId(), courseId)
                .orElseGet(() -> {
                    CourseCertificate created = new CourseCertificate();
                    created.setUser(user);
                    created.setCourseId(courseId);
                    created.setCourseTitle(resolveCourseTitle(courseId));
                    created.setVerificationCode(UUID.randomUUID().toString().replace("-", ""));
                    created.setIssuedAt(enrollment.getCompletedAt());
                    return certificates.save(created);
                });

        if (!enrollment.getCompletedAt().equals(certificate.getIssuedAt())) {
            certificate.setIssuedAt(enrollment.getCompletedAt());
            certificate = certificates.save(certificate);
        }
        if (isBlank(certificate.getCourseTitle())) {
            certificate.setCourseTitle(resolveCourseTitle(courseId));
            certificate = certificates.save(certificate);
        }
        return response(certificate);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> verify(String code) {
        return response(find(code));
    }

    @Transactional
    public byte[] pdf(String code) {
        CourseCertificate certificate = find(code);
        CourseStudentEnrollment enrollment = courseEnrollmentProgressService
                .refreshCompletion(certificate.getUser(), certificate.getCourseId())
                .orElse(null);
        if (enrollment != null && enrollment.getCompletedAt() != null
                && !enrollment.getCompletedAt().equals(certificate.getIssuedAt())) {
            certificate.setIssuedAt(enrollment.getCompletedAt());
            certificate = certificates.save(certificate);
        }
        if (isBlank(certificate.getCourseTitle())) {
            certificate.setCourseTitle(resolveCourseTitle(certificate.getCourseId()));
            certificate = certificates.save(certificate);
        }

        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);

            var templateSettings = templateService.getForRendering();
            BufferedImage backgroundImage = templateService.readImage(templateSettings.backgroundUrl());
            if (backgroundImage == null) {
                try (InputStream templateInput = resource(TEMPLATE_PATH).getInputStream()) {
                    backgroundImage = ImageIO.read(templateInput);
                }
            }

            PDImageXObject template = PDImageXObject.createFromByteArray(
                    document,
                    imageBytes(backgroundImage),
                    "carbonedge-certificate-template");
            BufferedImage overlayImage = renderDynamicContent(certificate, templateSettings);
            PDImageXObject overlay = LosslessFactory.createFromImage(document, overlayImage);

            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.drawImage(template, 0, 0, page.getMediaBox().getWidth(), page.getMediaBox().getHeight());
                content.drawImage(overlay, 0, 0, page.getMediaBox().getWidth(), page.getMediaBox().getHeight());
            }

            PDDocumentInformation information = document.getDocumentInformation();
            information.setTitle("ใบประกาศนียบัตร - " + learnerName(certificate));
            information.setAuthor("Carbon Edge");
            information.setSubject(certificate.getCourseTitle());
            document.save(output);
            return output.toByteArray();
        } catch (Exception exception) {
            throw new IllegalStateException("Could not generate certificate PDF", exception);
        }
    }

    private byte[] imageBytes(BufferedImage image) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
    }

    private BufferedImage renderDynamicContent(
            CourseCertificate certificate,
            com.carbonedge.backend.dto.CertificateTemplateSettings template
    ) throws Exception {
        BufferedImage overlay = new BufferedImage(TEMPLATE_WIDTH, TEMPLATE_HEIGHT, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = overlay.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            Font regular = loadFont(REGULAR_FONT_PATH);
            Font bold = loadFont(BOLD_FONT_PATH);
            var elements = template.elements().stream().collect(java.util.stream.Collectors.toMap(
                    com.carbonedge.backend.dto.CertificateTemplateElement::key, element -> element));
            drawFixedCopy(graphics, template, regular, bold);
            drawImageElement(graphics, templateService.readImage(template.logoUrl()), elements.get("logo"));
            drawImageElement(graphics, templateService.readImage(template.signatureUrl()), elements.get("signature"));
            drawTextElement(graphics, learnerName(certificate), bold, elements.get("name"), false);
            drawTextElement(graphics, certificate.getCourseTitle(), bold, elements.get("course"), true);
            drawTextElement(graphics, thaiIssuedDate(certificate.getIssuedAt()), regular, elements.get("date"), false);
            drawTextElement(graphics, "Certificate ID: " + certificate.getVerificationCode(), regular,
                    elements.get("certificateId"), false);
            var qrElement = elements.get("qr");
            if (qrElement != null && qrElement.enabled()) {
                int x = pxX(qrElement.x());
                int y = pxY(qrElement.y());
                int width = pxX(qrElement.width());
                int height = pxY(qrElement.height());
                int size = Math.max(1, Math.min(width, height));
                BufferedImage qr = qrCode(verificationUrl(certificate), size);
                graphics.setColor(Color.WHITE);
                graphics.fillRoundRect(x, y, width, height, 10, 10);
                graphics.drawImage(qr, x + (width - size) / 2, y + (height - size) / 2, size, size, null);
            }
        } finally {
            graphics.dispose();
        }
        return overlay;
    }

    private void drawFixedCopy(Graphics2D graphics,
            com.carbonedge.backend.dto.CertificateTemplateSettings template, Font regular, Font bold) {
        drawFixedText(graphics, template.certificateTitle(), bold, 64, 22, 28.5, 56, 6, BRAND_GREEN);
        drawFixedText(graphics, template.recipientIntro(), regular, 30, 32, 36.5, 36, 3.8, BRAND_GREEN);
        drawFixedText(graphics, template.courseIntro(), regular, 22, 28, 54, 44, 3.5, BRAND_GREEN);
        drawFixedText(graphics, template.achievementLine1(), regular, 22, 15, 74.5, 70, 3, new Color(130, 130, 130));
        drawFixedText(graphics, template.achievementLine2(), regular, 19, 12, 77, 76, 3.5, new Color(130, 130, 130));
        if (template.signatoryName() != null && !template.signatoryName().isBlank()) {
            drawFixedText(graphics, template.signatoryName(), bold, 23, 32, 84, 36, 3, BRAND_GREEN);
        }
        if (template.signatoryTitle() != null && !template.signatoryTitle().isBlank()) {
            drawFixedText(graphics, template.signatoryTitle(), regular, 20, 32, 87, 36, 3, BRAND_GREEN);
        }
    }

    private void drawFixedText(Graphics2D graphics, String text, Font font, float fontSize,
            double xPercent, double yPercent, double widthPercent, double heightPercent, Color color) {
        int x = pxX(xPercent);
        int y = pxY(yPercent);
        int width = pxX(widthPercent);
        int height = pxY(heightPercent);
        if (text == null || text.isBlank()) return;
        graphics.setColor(color);
        Font configured = fitFont(graphics, font, text, fontSize, 12, width);
        graphics.setFont(configured);
        int centerX = x + width / 2;
        int baselineY = y + height * 3 / 4;
        int textWidth = graphics.getFontMetrics().stringWidth(text);
        graphics.drawString(text, centerX - textWidth / 2, baselineY);
    }

    private void drawTextElement(Graphics2D graphics, String text, Font font,
            com.carbonedge.backend.dto.CertificateTemplateElement element, boolean wrap) {
        if (element == null || !element.enabled()) return;
        graphics.setColor(Color.decode(element.color()));
        int maxWidth = Math.max(1, pxX(element.width()));
        int centerX = pxX(element.x()) + maxWidth / 2;
        int baselineY = pxY(element.y()) + Math.max(1, pxY(element.height()) * 3 / 4);
        Font configured = font.deriveFont((float) element.fontSize());
        configured = fitFont(graphics, configured, text, (float) element.fontSize(), 8, maxWidth);
        graphics.setFont(configured);
        if (!wrap || graphics.getFontMetrics().stringWidth(text) <= maxWidth) {
            int drawX = "certificateId".equals(element.key())
                    ? pxX(element.x())
                    : centerX - graphics.getFontMetrics().stringWidth(text) / 2;
            graphics.drawString(text, drawX, baselineY);
            return;
        }
        List<String> lines = wrapText(graphics, text, maxWidth, 2);
        int lineHeight = graphics.getFontMetrics().getHeight();
        int firstBaseline = baselineY - (lines.size() - 1) * lineHeight / 2;
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            graphics.drawString(line, centerX - graphics.getFontMetrics().stringWidth(line) / 2,
                    firstBaseline + i * lineHeight);
        }
    }

    private void drawImageElement(Graphics2D graphics, BufferedImage image,
            com.carbonedge.backend.dto.CertificateTemplateElement element) {
        if (image == null || element == null || !element.enabled()) return;
        int x = pxX(element.x());
        int y = pxY(element.y());
        int width = pxX(element.width());
        int height = pxY(element.height());
        graphics.setColor(Color.WHITE);
        graphics.fillRect(x, y, width, height);
        graphics.drawImage(image, x, y, width, height, null);
    }

    private int pxX(double percent) { return (int) Math.round(TEMPLATE_WIDTH * percent / 100.0); }
    private int pxY(double percent) { return (int) Math.round(TEMPLATE_HEIGHT * percent / 100.0); }

    private void drawCenteredFitted(
            Graphics2D graphics,
            String text,
            Font font,
            float preferredSize,
            float minimumSize,
            int maximumWidth,
            int baselineY
    ) {
        Font fitted = fitFont(graphics, font, text, preferredSize, minimumSize, maximumWidth);
        graphics.setFont(fitted);
        FontMetrics metrics = graphics.getFontMetrics();
        int x = (TEMPLATE_WIDTH - metrics.stringWidth(text)) / 2;
        graphics.drawString(text, x, baselineY);
    }

    private void drawLeftFitted(
            Graphics2D graphics,
            String text,
            Font font,
            float preferredSize,
            float minimumSize,
            int maximumWidth,
            int x,
            int baselineY
    ) {
        Font fitted = fitFont(graphics, font, text, preferredSize, minimumSize, maximumWidth);
        graphics.setFont(fitted);
        graphics.drawString(text, x, baselineY);
    }

    private void drawWrappedCentered(
            Graphics2D graphics,
            String text,
            Font font,
            float preferredSize,
            float minimumSize,
            int maximumWidth,
            int firstBaselineY,
            int lineHeight,
            int maximumLines
    ) {
        Font fitted = fitFont(graphics, font, text, preferredSize, minimumSize, maximumWidth);
        graphics.setFont(fitted);
        if (graphics.getFontMetrics().stringWidth(text) <= maximumWidth) {
            drawCentered(graphics, text, firstBaselineY + lineHeight / 2);
            return;
        }

        List<String> lines = wrapText(graphics, text, maximumWidth, maximumLines);
        int startY = firstBaselineY - ((lines.size() - 1) * lineHeight) / 2;
        for (int index = 0; index < lines.size(); index++) {
            drawCentered(graphics, lines.get(index), startY + index * lineHeight);
        }
    }

    private List<String> wrapText(Graphics2D graphics, String text, int maximumWidth, int maximumLines) {
        String[] words = text.trim().split("\\s+");
        List<String> lines = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String word : words) {
            String candidate = current.isEmpty() ? word : current + " " + word;
            if (!current.isEmpty() && graphics.getFontMetrics().stringWidth(candidate) > maximumWidth
                    && lines.size() < maximumLines - 1) {
                lines.add(current.toString());
                current = new StringBuilder(word);
            } else {
                current = new StringBuilder(candidate);
            }
        }
        if (!current.isEmpty()) {
            lines.add(current.toString());
        }
        return lines;
    }

    private void drawCentered(Graphics2D graphics, String text, int baselineY) {
        int x = (TEMPLATE_WIDTH - graphics.getFontMetrics().stringWidth(text)) / 2;
        graphics.drawString(text, x, baselineY);
    }

    private Font fitFont(
            Graphics2D graphics,
            Font font,
            String text,
            float preferredSize,
            float minimumSize,
            int maximumWidth
    ) {
        float size = preferredSize;
        Font candidate = font.deriveFont(size);
        while (size > minimumSize && graphics.getFontMetrics(candidate).stringWidth(text) > maximumWidth) {
            size -= 1;
            candidate = font.deriveFont(size);
        }
        return candidate;
    }

    private Font loadFont(String path) throws Exception {
        try (InputStream input = resource(path).getInputStream()) {
            return Font.createFont(Font.TRUETYPE_FONT, input);
        }
    }

    private BufferedImage qrCode(String value, int size) throws Exception {
        BitMatrix matrix = new QRCodeWriter().encode(value, BarcodeFormat.QR_CODE, size, size);
        return MatrixToImageWriter.toBufferedImage(matrix);
    }

    private String thaiIssuedDate(Instant issuedAt) {
        ZonedDateTime date = issuedAt.atZone(BANGKOK_ZONE);
        return "ให้ไว้ ณ วันที่ " + date.getDayOfMonth()
                + " " + THAI_MONTHS[date.getMonthValue() - 1]
                + " " + (date.getYear() + 543);
    }

    private String learnerName(CourseCertificate certificate) {
        String name = (certificate.getUser().getFirstName() + " " + certificate.getUser().getLastName()).trim();
        return name.isBlank() ? certificate.getUser().getUsername() : name;
    }

    private String resolveCourseTitle(Long courseId) {
        MoodleCourseRecord course = moodleClient.getCourseById(courseId);
        if (!isBlank(course.displayName())) {
            return course.displayName().trim();
        }
        if (!isBlank(course.fullName())) {
            return course.fullName().trim();
        }
        return "Course #" + courseId;
    }

    private CourseCertificate find(String code) {
        return certificates.findByVerificationCode(code)
                .orElseThrow(() -> new NotFoundException("Certificate not found"));
    }

    private Map<String, Object> response(CourseCertificate certificate) {
        return Map.of(
                "verificationCode", certificate.getVerificationCode(),
                "courseId", certificate.getCourseId(),
                "courseTitle", certificate.getCourseTitle() == null ? "" : certificate.getCourseTitle(),
                "issuedAt", certificate.getIssuedAt(),
                "learnerName", learnerName(certificate),
                "verificationUrl", verificationUrl(certificate),
                "pdfUrl", publicBaseUrl.replaceAll("/$", "")
                        + "/certificates/" + certificate.getVerificationCode() + "/pdf");
    }

    private String verificationUrl(CourseCertificate certificate) {
        return frontendBaseUrl.replaceAll("/$", "")
                + "/certificates/verify/" + certificate.getVerificationCode();
    }

    private ClassPathResource resource(String path) {
        return new ClassPathResource(path);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
