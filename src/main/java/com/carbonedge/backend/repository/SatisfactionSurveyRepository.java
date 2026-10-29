package com.carbonedge.backend.repository;

import com.carbonedge.backend.model.SatisfactionSurvey;
import com.carbonedge.backend.model.SatisfactionSurveyStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SatisfactionSurveyRepository extends JpaRepository<SatisfactionSurvey, Long> {
    List<SatisfactionSurvey> findAllByOrderByUpdatedAtDesc();
    List<SatisfactionSurvey> findAllByStatusOrderByOpensAtAsc(SatisfactionSurveyStatus status);
    List<SatisfactionSurvey> findAllByCourseId(Long courseId);
    List<SatisfactionSurvey> findAllByCourseIdAndStatus(Long courseId, SatisfactionSurveyStatus status);
    Optional<SatisfactionSurvey> findByDefaultForCourseId(Long courseId);
    List<SatisfactionSurvey> findAllByDefaultForCourseIdIsNotNull();
}
