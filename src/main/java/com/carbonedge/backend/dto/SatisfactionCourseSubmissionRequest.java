package com.carbonedge.backend.dto;

import java.util.List;

public record SatisfactionCourseSubmissionRequest(List<Answer> answers) {
    public record Answer(Long surveyId, Long questionId, String answer) { }
}
