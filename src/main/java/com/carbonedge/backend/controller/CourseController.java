package com.carbonedge.backend.controller;

import com.carbonedge.backend.dto.CourseLaunchResponse;
import com.carbonedge.backend.dto.CourseResponse;
import com.carbonedge.backend.service.CourseService;
import java.util.List;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/courses")
@RequiredArgsConstructor
public class CourseController {

    private final CourseService courseService;

    @GetMapping("/my")
    public List<CourseResponse> getMyCourses(@RequestHeader("Authorization") String authorizationHeader) {
        return courseService.getMyCourses(authorizationHeader);
    }

    @GetMapping("/{id}")
    public CourseResponse getMyCourseById(
            @RequestHeader("Authorization") String authorizationHeader,
            @PathVariable("id") Long courseId
    ) {
        return courseService.getMyCourseById(authorizationHeader, courseId);
    }

    @GetMapping("/{id}/launch")
    public CourseLaunchResponse getMyCourseLaunch(
            @RequestHeader("Authorization") String authorizationHeader,
            @PathVariable("id") Long courseId
    ) {
        return courseService.getMyCourseLaunch(authorizationHeader, courseId);
    }
}
