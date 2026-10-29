package com.carbonedge.backend.repository;

import com.carbonedge.backend.model.SatisfactionQuestion;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SatisfactionQuestionRepository extends JpaRepository<SatisfactionQuestion, Long> {
    List<SatisfactionQuestion> findAllBySurveyIdOrderByOrderIndexAscIdAsc(Long surveyId);
    List<SatisfactionQuestion> findAllBySurveyIdAndActiveTrueOrderByOrderIndexAscIdAsc(Long surveyId);
    Optional<SatisfactionQuestion> findByIdAndSurveyId(Long id, Long surveyId);
    long countBySurveyIdAndActiveTrue(Long surveyId);
    void deleteAllBySurveyId(Long surveyId);
}
