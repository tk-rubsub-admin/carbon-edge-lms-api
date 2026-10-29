package com.carbonedge.backend.controller;

import com.carbonedge.backend.dto.CourseTemplateResponse;
import com.carbonedge.backend.dto.CreateCourseTemplateRequest;
import com.carbonedge.backend.dto.UpdateCourseTemplateRequest;
import com.carbonedge.backend.service.CourseService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/course-templates")
@RequiredArgsConstructor
public class CourseTemplateController {

    private final CourseService courseService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CourseTemplateResponse createTemplate(
            @RequestHeader("Authorization") String authorizationHeader,
            @Valid @RequestBody CreateCourseTemplateRequest request
    ) {
        return courseService.createCourseTemplate(authorizationHeader, request);
    }

    @GetMapping
    public List<CourseTemplateResponse> listTemplates(
            @RequestHeader("Authorization") String authorizationHeader
    ) {
        return courseService.getCourseTemplates(authorizationHeader);
    }

    @GetMapping("/{id}")
    public CourseTemplateResponse getTemplate(
            @RequestHeader("Authorization") String authorizationHeader,
            @PathVariable("id") Long templateId
    ) {
        return courseService.getCourseTemplate(authorizationHeader, templateId);
    }

    @PatchMapping("/{id}")
    public CourseTemplateResponse updateTemplate(
            @RequestHeader("Authorization") String authorizationHeader,
            @PathVariable("id") Long templateId,
            @Valid @RequestBody UpdateCourseTemplateRequest request
    ) {
        return courseService.updateCourseTemplate(authorizationHeader, templateId, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteTemplate(
            @RequestHeader("Authorization") String authorizationHeader,
            @PathVariable("id") Long templateId
    ) {
        courseService.deleteCourseTemplate(authorizationHeader, templateId);
    }
}
