package com.carbonedge.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.carbonedge.backend.client.moodle.MoodleClient;
import com.carbonedge.backend.model.CourseCertificate;
import com.carbonedge.backend.dto.CertificateTemplateElement;
import com.carbonedge.backend.dto.CertificateTemplateSettings;
import com.carbonedge.backend.model.CourseStudentEnrollment;
import com.carbonedge.backend.model.UserAccount;
import com.carbonedge.backend.repository.CourseCertificateRepository;
import com.carbonedge.backend.repository.SatisfactionResponseRepository;
import com.carbonedge.backend.repository.SatisfactionSurveyRepository;
import com.carbonedge.backend.repository.UserAccountRepository;
import java.awt.image.BufferedImage;
import java.time.Instant;
import java.util.Optional;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class CertificateServiceTests {

    @Test
    void generatesThaiCertificateFromCarbonEdgeTemplate() throws Exception {
        CourseCertificateRepository certificates = mock(CourseCertificateRepository.class);
        CourseCertificate certificate = certificate();
        CourseEnrollmentProgressService progressService = mock(CourseEnrollmentProgressService.class);
        CourseStudentEnrollment enrollment = new CourseStudentEnrollment();
        enrollment.setUser(certificate.getUser());
        enrollment.setCourseId(certificate.getCourseId());
        enrollment.setStartedAt(Instant.parse("2026-07-15T02:00:00Z"));
        enrollment.setCompletedAt(Instant.parse("2026-07-15T03:00:00Z"));
        when(certificates.findByVerificationCode("CERT-TH-001")).thenReturn(Optional.of(certificate));
        when(certificates.save(certificate)).thenReturn(certificate);
        when(progressService.refreshCompletion(certificate.getUser(), certificate.getCourseId()))
                .thenReturn(Optional.of(enrollment));

        CertificateTemplateService templateService = mock(CertificateTemplateService.class);
        when(templateService.getForRendering()).thenReturn(new CertificateTemplateSettings(null, null, null, java.util.List.of(
                new CertificateTemplateElement("name", "ชื่อผู้เรียน", 12, 44, 76, 7, 36, "#0B4A31", true),
                new CertificateTemplateElement("course", "ชื่อหลักสูตร", 10, 56, 80, 9, 29, "#0B4A31", true),
                new CertificateTemplateElement("date", "วันที่ออกใบประกาศ", 15, 68, 70, 6, 20, "#0B4A31", true),
                new CertificateTemplateElement("qr", "QR Code", 4, 88, 12, 9, 0, "#0B4A31", true),
                new CertificateTemplateElement("certificateId", "Certificate ID", 17, 91, 55, 4, 12, "#0B4A31", true),
                new CertificateTemplateElement("logo", "โลโก้", 35, 5, 30, 18, 0, "#0B4A31", false),
                new CertificateTemplateElement("signature", "ลายเซ็น", 38, 81, 24, 2.7, 0, "#0B4A31", false)),
                "ใบประกาศนียบัตร", "ฉบับนี้ให้ไว้เพื่อแสดงว่า", "ได้ผ่านการอบรมและทดสอบตามหลักสูตร",
                "และผ่านการประเมินตามเกณฑ์ที่กำหนด", "ขอให้มีความมุ่งมั่นพัฒนาองค์ความรู้", "", ""));
        CertificateService service = new CertificateService(
                mock(AuthService.class),
                mock(UserAccountRepository.class),
                certificates,
                progressService,
                mock(MoodleClient.class),
                templateService,
                mock(SatisfactionSurveyRepository.class),
                mock(SatisfactionResponseRepository.class),
                mock(SatisfactionSurveyService.class));
        ReflectionTestUtils.setField(service, "publicBaseUrl", "http://localhost:8086/lms-api");
        ReflectionTestUtils.setField(service, "frontendBaseUrl", "http://localhost:4000");

        byte[] pdf = service.pdf("CERT-TH-001");

        assertThat(pdf).startsWith("%PDF".getBytes()).hasSizeGreaterThan(100_000);
        assertThat(certificate.getIssuedAt()).isEqualTo(enrollment.getCompletedAt());
        verify(certificates).save(certificate);
        try (PDDocument document = Loader.loadPDF(pdf)) {
            assertThat(document.getNumberOfPages()).isEqualTo(1);
            assertThat(document.getPage(0).getMediaBox().getWidth()).isEqualTo(595.27563f);
            assertThat(document.getPage(0).getMediaBox().getHeight()).isEqualTo(841.8898f);

            BufferedImage preview = new PDFRenderer(document).renderImageWithDPI(0, 110);
            assertThat(preview.getWidth()).isGreaterThan(800);
            assertThat(preview.getHeight()).isGreaterThan(1_200);
        }
    }

    private CourseCertificate certificate() {
        UserAccount user = new UserAccount();
        user.setUsername("thai-student");
        user.setFirstName("นางสาวกัญญารา");
        user.setLastName("ชายกลิ่น");

        CourseCertificate certificate = new CourseCertificate();
        certificate.setVerificationCode("CERT-TH-001");
        certificate.setUser(user);
        certificate.setCourseId(42L);
        certificate.setCourseTitle("การจัดการคาร์บอนและการใช้ไบโอชาร์");
        certificate.setIssuedAt(Instant.parse("2026-09-29T03:00:00Z"));
        return certificate;
    }
}
