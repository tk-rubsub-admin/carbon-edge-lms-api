package com.carbonedge.backend.repository;

import com.carbonedge.backend.model.SatisfactionAnswer;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.carbonedge.backend.model.SatisfactionQuestionType;

public interface SatisfactionAnswerRepository extends JpaRepository<SatisfactionAnswer, Long> {
    @Query("select answer from SatisfactionAnswer answer where answer.response.survey.id = :surveyId order by answer.response.submittedAt desc")
    List<SatisfactionAnswer> findAllBySurveyId(@Param("surveyId") Long surveyId);

    @Query("select answer from SatisfactionAnswer answer where answer.response.survey.courseId = :courseId and answer.questionTypeSnapshot = :type")
    List<SatisfactionAnswer> findAllByCourseIdAndQuestionType(@Param("courseId") Long courseId,
            @Param("type") SatisfactionQuestionType type);
}
