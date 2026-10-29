package com.carbonedge.backend.repository;

import com.carbonedge.backend.model.CourseSection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CourseSectionRepository extends JpaRepository<CourseSection, Long> {

    List<CourseSection> findAllByCourseIdOrderByOrderIndexAscIdAsc(Long courseId);

    List<CourseSection> findAllByCourseId(Long courseId);

    Optional<CourseSection> findByIdAndCourseId(Long id, Long courseId);

    Optional<CourseSection> findFirstByCourseIdOrderByOrderIndexDescIdDesc(Long courseId);

    void deleteByCourseId(Long courseId);
}
