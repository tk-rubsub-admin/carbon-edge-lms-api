package com.carbonedge.backend.dto;

import java.util.List;

public record SatisfactionSubmissionRequest(List<Answer> answers) {
    public record Answer(Long questionId, String answer) { }
}
