package com.carbonedge.backend.dto;

import com.carbonedge.backend.model.SatisfactionQuestionType;
import com.carbonedge.backend.model.SatisfactionSurveyStatus;
import java.time.Instant;
import java.util.List;

public record SatisfactionSurveyResponse(Long id, Long courseId, String title, String description,
        SatisfactionSurveyStatus status, Instant opensAt, Instant closesAt, long responseCount,
        List<Question> questions, boolean submitted, boolean defaultSurvey) {
    public record Question(Long id, SatisfactionQuestionType type, String prompt, List<String> options,
            boolean required, Integer orderIndex) { }
}
