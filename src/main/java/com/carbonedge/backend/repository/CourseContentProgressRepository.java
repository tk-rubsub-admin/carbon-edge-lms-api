package com.carbonedge.backend.repository;

import com.carbonedge.backend.model.CourseContentProgress;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CourseContentProgressRepository extends JpaRepository<CourseContentProgress, Long> {
    Optional<CourseContentProgress> findByUserIdAndContentItemId(Long userId, Long contentItemId);

    List<CourseContentProgress> findAllByUserIdAndCourseId(Long userId, Long courseId);
    List<CourseContentProgress> findAllByUserId(Long userId);

    @Modifying
    @Query("delete from CourseContentProgress progress where progress.contentItem.id = :itemId")
    void deleteByContentItemId(@Param("itemId") Long itemId);

    @Modifying
    @Query("delete from CourseContentProgress progress where progress.contentItem.section.id = :sectionId")
    void deleteBySectionId(@Param("sectionId") Long sectionId);
}
