package com.carbonedge.backend.controller;

import com.carbonedge.backend.dto.SatisfactionSubmissionRequest;
import com.carbonedge.backend.dto.SatisfactionSurveyResponse;
import com.carbonedge.backend.dto.SatisfactionSurveySaveRequest;
import com.carbonedge.backend.dto.SatisfactionDefaultQuestionsRequest;
import com.carbonedge.backend.dto.SatisfactionCourseSubmissionRequest;
import com.carbonedge.backend.service.SatisfactionSurveyService;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/satisfaction")
@RequiredArgsConstructor
public class SatisfactionSurveyController {
    private final SatisfactionSurveyService service;

    @GetMapping("/admin")
    public List<SatisfactionSurveyResponse> listAdmin(@RequestHeader("Authorization") String authorization) {
        return service.listAdmin(authorization);
    }

    @GetMapping("/admin/{surveyId}")
    public SatisfactionSurveyResponse getAdmin(@RequestHeader("Authorization") String authorization, @PathVariable Long surveyId) {
        return service.getAdmin(authorization, surveyId);
    }

    @GetMapping("/admin/default-questions")
    public List<Map<String, Object>> getDefaultQuestions(@RequestHeader("Authorization") String authorization) {
        return service.getDefaultQuestions(authorization);
    }

    @PutMapping("/admin/default-questions")
    public List<Map<String, Object>> saveDefaultQuestions(@RequestHeader("Authorization") String authorization,
            @RequestBody SatisfactionDefaultQuestionsRequest request) {
        return service.saveDefaultQuestions(authorization, request);
    }

    @PostMapping("/admin")
    @ResponseStatus(HttpStatus.CREATED)
    public SatisfactionSurveyResponse create(@RequestHeader("Authorization") String authorization,
            @RequestBody SatisfactionSurveySaveRequest request) {
        return service.save(authorization, null, request);
    }

    @PutMapping("/admin/{surveyId}")
    public SatisfactionSurveyResponse update(@RequestHeader("Authorization") String authorization,
            @PathVariable Long surveyId, @RequestBody SatisfactionSurveySaveRequest request) {
        return service.save(authorization, surveyId, request);
    }

    @DeleteMapping("/admin/{surveyId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@RequestHeader("Authorization") String authorization, @PathVariable Long surveyId) {
        service.delete(authorization, surveyId);
    }

    @GetMapping("/admin/{surveyId}/results")
    public Map<String, Object> results(@RequestHeader("Authorization") String authorization, @PathVariable Long surveyId) {
        return service.results(authorization, surveyId);
    }

    @GetMapping("/available")
    public List<SatisfactionSurveyResponse> available(@RequestHeader("Authorization") String authorization) {
        return service.available(authorization);
    }

    @GetMapping("/courses/{courseId}/student-status")
    public Map<String, Object> studentCourseStatus(@RequestHeader("Authorization") String authorization,
            @PathVariable Long courseId) {
        return service.studentCourseStatus(authorization, courseId);
    }

    @GetMapping("/courses/{courseId}/summary")
    public Map<String, Object> courseSummary(@RequestHeader("Authorization") String authorization,
            @PathVariable Long courseId) {
        return service.courseSummary(authorization, courseId);
    }

    @GetMapping("/courses/{courseId}/questionnaire")
    public Map<String, Object> studentQuestionnaire(@RequestHeader("Authorization") String authorization,
            @PathVariable Long courseId) {
        return service.studentQuestionnaire(authorization, courseId);
    }

    @PostMapping("/courses/{courseId}/questionnaire/responses")
    public Map<String, Object> submitCourseQuestionnaire(@RequestHeader("Authorization") String authorization,
            @PathVariable Long courseId, @RequestBody SatisfactionCourseSubmissionRequest request) {
        return service.submitCourseQuestionnaire(authorization, courseId, request);
    }

    @GetMapping("/{surveyId}")
    public SatisfactionSurveyResponse getForStudent(@RequestHeader("Authorization") String authorization, @PathVariable Long surveyId) {
        return service.getForStudent(authorization, surveyId);
    }

    @PostMapping("/{surveyId}/responses")
    public Map<String, Object> submit(@RequestHeader("Authorization") String authorization,
            @PathVariable Long surveyId, @RequestBody SatisfactionSubmissionRequest request) {
        return service.submit(authorization, surveyId, request);
    }
}
