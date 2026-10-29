package com.carbonedge.backend.repository;

import com.carbonedge.backend.model.CourseTemplate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CourseTemplateRepository extends JpaRepository<CourseTemplate, Long> {

    Optional<CourseTemplate> findByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCase(String code);
}
