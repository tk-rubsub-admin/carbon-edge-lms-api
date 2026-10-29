package com.carbonedge.backend.dto;
import java.util.List;
public record MoodleQuizAnswerRequest(Long questionId, List<Long> optionIds) { }
