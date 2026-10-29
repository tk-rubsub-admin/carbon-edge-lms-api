package com.carbonedge.backend.controller;

import com.carbonedge.backend.dto.CourseResponse;
import com.carbonedge.backend.dto.SearchCoursesRequest;
import com.carbonedge.backend.service.CourseService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/courses")
@RequiredArgsConstructor
public class V1CourseController {

    private final CourseService courseService;

    @PostMapping
    public List<CourseResponse> searchCourses(
            @RequestHeader("Authorization") String authorizationHeader,
            @Valid @RequestBody(required = false) SearchCoursesRequest request
    ) {
        return courseService.searchSystemCourses(authorizationHeader, request);
    }
}
