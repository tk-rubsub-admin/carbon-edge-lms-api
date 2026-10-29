package com.carbonedge.backend.repository;

import com.carbonedge.backend.model.CourseCertificate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CourseCertificateRepository extends JpaRepository<CourseCertificate, Long> {
    Optional<CourseCertificate> findByUserIdAndCourseId(Long userId, Long courseId);
    Optional<CourseCertificate> findByVerificationCode(String verificationCode);
}
