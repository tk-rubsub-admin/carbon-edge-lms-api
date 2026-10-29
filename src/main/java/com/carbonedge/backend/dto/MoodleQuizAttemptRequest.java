package com.carbonedge.backend.dto;
import jakarta.validation.constraints.NotNull; import java.util.List;
public record MoodleQuizAttemptRequest(@NotNull Long moodleUserId, List<MoodleQuizAnswerRequest> answers) { }
