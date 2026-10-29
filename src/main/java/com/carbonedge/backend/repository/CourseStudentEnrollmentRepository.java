package com.carbonedge.backend.repository;

import com.carbonedge.backend.model.CourseStudentEnrollment;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;

public interface CourseStudentEnrollmentRepository extends JpaRepository<CourseStudentEnrollment, Long> {

    List<CourseStudentEnrollment> findAllByCourseId(Long courseId);

    List<CourseStudentEnrollment> findAllByUserId(Long userId);

    long countByCourseId(Long courseId);

    Page<CourseStudentEnrollment> findByCourseId(Long courseId, Pageable pageable);

    @Query("select enrollment.user.id from CourseStudentEnrollment enrollment where enrollment.courseId = :courseId")
    List<Long> findUserIdsByCourseId(@Param("courseId") Long courseId);

    boolean existsByUserIdAndCourseId(Long userId, Long courseId);

    Optional<CourseStudentEnrollment> findByUserIdAndCourseId(Long userId, Long courseId);

    void deleteByUserIdAndCourseId(Long userId, Long courseId);

    void deleteByCourseId(Long courseId);
}
