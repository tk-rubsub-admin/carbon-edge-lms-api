package com.carbonedge.backend.model;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter @Entity @Table(name = "certificates")
public class CourseCertificate {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "verification_code", nullable = false, unique = true, length = 64) private String verificationCode;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "user_id", nullable = false) private UserAccount user;
    @Column(name = "course_id", nullable = false) private Long courseId;
    @Column(name = "course_title", length = 255) private String courseTitle;
    @Column(name = "issued_at", nullable = false) private Instant issuedAt;
}
