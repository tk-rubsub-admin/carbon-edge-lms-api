package com.carbonedge.backend.repository;

import com.carbonedge.backend.model.CourseTeacherAssignment;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CourseTeacherAssignmentRepository extends JpaRepository<CourseTeacherAssignment, Long> {

    Optional<CourseTeacherAssignment> findByUserIdAndCourseId(Long userId, Long courseId);

    List<CourseTeacherAssignment> findAllByUserId(Long userId);

    List<CourseTeacherAssignment> findAllByCourseId(Long courseId);

    boolean existsByUserIdAndCourseId(Long userId, Long courseId);

    void deleteByUserIdAndCourseId(Long userId, Long courseId);

    void deleteByCourseId(Long courseId);
}
