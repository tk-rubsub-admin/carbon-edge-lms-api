package com.carbonedge.backend.repository;

import com.carbonedge.backend.model.SatisfactionResponse;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SatisfactionResponseRepository extends JpaRepository<SatisfactionResponse, Long> {
    long countBySurveyId(Long surveyId);
    boolean existsBySurveyIdAndUserId(Long surveyId, Long userId);
    Optional<SatisfactionResponse> findBySurveyIdAndUserId(Long surveyId, Long userId);
    @Query("select count(response) from SatisfactionResponse response where response.survey.courseId = :courseId")
    long countByCourseId(@Param("courseId") Long courseId);
}
