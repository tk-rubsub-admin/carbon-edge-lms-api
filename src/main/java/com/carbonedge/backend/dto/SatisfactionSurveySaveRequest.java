package com.carbonedge.backend.dto;

import com.carbonedge.backend.model.SatisfactionSurveyStatus;
import java.time.Instant;
import java.util.List;

public record SatisfactionSurveySaveRequest(Long courseId, String title, String description,
        SatisfactionSurveyStatus status, Instant opensAt, Instant closesAt,
        List<SatisfactionQuestionRequest> questions) { }
