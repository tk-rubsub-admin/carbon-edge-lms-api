package com.carbonedge.backend.repository;

import com.carbonedge.backend.model.CourseContentItem;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CourseContentItemRepository extends JpaRepository<CourseContentItem, Long> {

    List<CourseContentItem> findAllBySectionIdOrderByOrderIndexAscIdAsc(Long sectionId);

    List<CourseContentItem> findAllBySection_CourseIdOrderBySection_OrderIndexAscOrderIndexAscIdAsc(Long courseId);

    Optional<CourseContentItem> findByIdAndSectionId(Long id, Long sectionId);

    Optional<CourseContentItem> findFirstBySectionIdOrderByOrderIndexDescIdDesc(Long sectionId);

    void deleteBySectionId(Long sectionId);

    void deleteBySectionIdIn(Collection<Long> sectionIds);
}
