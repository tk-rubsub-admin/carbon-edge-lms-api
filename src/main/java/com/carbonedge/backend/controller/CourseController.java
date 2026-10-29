package com.carbonedge.backend.controller;

import com.carbonedge.backend.dto.CourseLaunchResponse;
import com.carbonedge.backend.dto.CourseContentFileResponse;
import com.carbonedge.backend.dto.CourseContentItemResponse;
import com.carbonedge.backend.dto.CourseDetailsResponse;
import com.carbonedge.backend.dto.CourseSectionResponse;
import com.carbonedge.backend.dto.CreateCourseRequest;
import com.carbonedge.backend.dto.DuplicateCourseRequest;
import com.carbonedge.backend.dto.CreateCourseContentItemRequest;
import com.carbonedge.backend.dto.CreateCourseSectionRequest;
import com.carbonedge.backend.dto.CourseResponse;
import com.carbonedge.backend.dto.CourseStudentPageResponse;
import com.carbonedge.backend.dto.BulkStudentEnrollmentResponse;
import com.carbonedge.backend.dto.CourseTeacherResponse;
import com.carbonedge.backend.dto.ReorderCourseContentItemRequest;
import com.carbonedge.backend.dto.ReorderCourseSectionRequest;
import com.carbonedge.backend.dto.UpdateCourseContentItemRequest;
import com.carbonedge.backend.dto.UpdateCourseSectionRequest;
import com.carbonedge.backend.dto.UpdateCourseRequest;
import com.carbonedge.backend.service.CourseService;
import java.util.List;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/courses")
@RequiredArgsConstructor
public class CourseController {

    private final CourseService courseService;

    @GetMapping("/my")
    public List<CourseResponse> getMyCourses(@RequestHeader("Authorization") String authorizationHeader) {
        return courseService.getMyCourses(authorizationHeader);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CourseResponse createCourse(
            @RequestHeader("Authorization") String authorizationHeader,
            @Valid @RequestBody CreateCourseRequest request
    ) {
        return courseService.createCourse(authorizationHeader, request);
    }

    @PostMapping("/import-template/{templateId}")
    @ResponseStatus(HttpStatus.CREATED)
    public CourseResponse importCourseFromTemplate(
            @RequestHeader("Authorization") String authorizationHeader,
            @PathVariable("templateId") Long templateId,
            @Valid @RequestBody CreateCourseRequest request
    ) {
        return courseService.importCourseFromTemplate(authorizationHeader, templateId, request);
    }

    @PostMapping("/{sourceCourseId}/duplicate")
    @ResponseStatus(HttpStatus.CREATED)
    public CourseResponse duplicateCourse(
            @RequestHeader("Authorization") String authorizationHeader,
            @PathVariable("sourceCourseId") Long sourceCourseId,
            @Valid @RequestBody DuplicateCourseRequest request
    ) {
        return courseService.duplicateCourse(authorizationHeader, sourceCourseId, request);
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

    @GetMapping("/{id}/details")
    public CourseDetailsResponse getCourseDetails(
            @RequestHeader("Authorization") String authorizationHeader,
            @PathVariable("id") Long courseId
    ) {
        return courseService.getCourseDetails(authorizationHeader, courseId);
    }

    @GetMapping("/{id}/content")
    public List<CourseSectionResponse> getCourseContent(
            @RequestHeader("Authorization") String authorizationHeader,
            @PathVariable("id") Long courseId
    ) {
        return courseService.getCourseContent(authorizationHeader, courseId);
    }

    @GetMapping("/{id}/teachers")
    public List<CourseTeacherResponse> getCourseTeachers(
            @RequestHeader("Authorization") String authorizationHeader,
            @PathVariable("id") Long courseId
    ) {
        return courseService.getCourseTeachers(authorizationHeader, courseId);
    }

    @GetMapping("/{id}/students")
    public CourseStudentPageResponse getCourseStudents(
            @RequestHeader("Authorization") String authorizationHeader,
            @PathVariable("id") Long courseId,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size
    ) {
        return courseService.getCourseStudents(authorizationHeader, courseId, page, size);
    }

    @GetMapping("/{id}/student-ids")
    public List<Long> getCourseStudentIds(
            @RequestHeader("Authorization") String authorizationHeader,
            @PathVariable("id") Long courseId
    ) {
        return courseService.getCourseStudentIds(authorizationHeader, courseId);
    }

    @PatchMapping("/{id}")
    public CourseResponse updateCourse(
            @RequestHeader("Authorization") String authorizationHeader,
            @PathVariable("id") Long courseId,
            @Valid @RequestBody UpdateCourseRequest request
    ) {
        return courseService.updateCourse(authorizationHeader, courseId, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCourse(
            @RequestHeader("Authorization") String authorizationHeader,
            @PathVariable("id") Long courseId
    ) {
        courseService.deleteCourse(authorizationHeader, courseId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/teachers/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void assignTeacher(
            @RequestHeader("Authorization") String authorizationHeader,
            @PathVariable("id") Long courseId,
            @PathVariable("userId") Long userId
    ) {
        courseService.assignTeacher(authorizationHeader, courseId, userId);
    }

    @PostMapping("/{id}/students/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void enrollStudent(
            @RequestHeader("Authorization") String authorizationHeader,
            @PathVariable("id") Long courseId,
            @PathVariable("userId") Long userId
    ) {
        courseService.enrollStudent(authorizationHeader, courseId, userId);
    }

    @PostMapping(
            value = "/{id}/students/bulk-upload",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public BulkStudentEnrollmentResponse bulkEnrollStudents(
            @RequestHeader("Authorization") String authorizationHeader,
            @PathVariable("id") Long courseId,
            @RequestParam("file") MultipartFile file
    ) {
        return courseService.bulkEnrollStudents(authorizationHeader, courseId, file);
    }

    @DeleteMapping("/{id}/students/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unenrollStudent(
            @RequestHeader("Authorization") String authorizationHeader,
            @PathVariable("id") Long courseId,
            @PathVariable("userId") Long userId
    ) {
        courseService.unenrollStudent(authorizationHeader, courseId, userId);
    }

    @DeleteMapping("/{id}/teachers/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unassignTeacher(
            @RequestHeader("Authorization") String authorizationHeader,
            @PathVariable("id") Long courseId,
            @PathVariable("userId") Long userId
    ) {
        courseService.unassignTeacher(authorizationHeader, courseId, userId);
    }

    @PostMapping("/{id}/sections")
    @ResponseStatus(HttpStatus.CREATED)
    public CourseSectionResponse createSection(
            @RequestHeader("Authorization") String authorizationHeader,
            @PathVariable("id") Long courseId,
            @Valid @RequestBody CreateCourseSectionRequest request
    ) {
        return courseService.createSection(authorizationHeader, courseId, request);
    }

    @PatchMapping("/{id}/sections/{sectionId}")
    public CourseSectionResponse updateSection(
            @RequestHeader("Authorization") String authorizationHeader,
            @PathVariable("id") Long courseId,
            @PathVariable("sectionId") Long sectionId,
            @Valid @RequestBody UpdateCourseSectionRequest request
    ) {
        return courseService.updateSection(authorizationHeader, courseId, sectionId, request);
    }

    @PatchMapping("/{id}/sections/{sectionId}/reorder")
    public CourseSectionResponse reorderSection(
            @RequestHeader("Authorization") String authorizationHeader,
            @PathVariable("id") Long courseId,
            @PathVariable("sectionId") Long sectionId,
            @Valid @RequestBody ReorderCourseSectionRequest request
    ) {
        return courseService.reorderSection(authorizationHeader, courseId, sectionId, request);
    }

    @DeleteMapping("/{id}/sections/{sectionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteSection(
            @RequestHeader("Authorization") String authorizationHeader,
            @PathVariable("id") Long courseId,
            @PathVariable("sectionId") Long sectionId
    ) {
        courseService.deleteSection(authorizationHeader, courseId, sectionId);
    }

    @PostMapping("/{id}/sections/{sectionId}/items")
    @ResponseStatus(HttpStatus.CREATED)
    public CourseContentItemResponse createContentItem(
            @RequestHeader("Authorization") String authorizationHeader,
            @PathVariable("id") Long courseId,
            @PathVariable("sectionId") Long sectionId,
            @Valid @RequestBody CreateCourseContentItemRequest request
    ) {
        return courseService.createContentItem(authorizationHeader, courseId, sectionId, request);
    }

    @PatchMapping("/{id}/sections/{sectionId}/items/{itemId}")
    public CourseContentItemResponse updateContentItem(
            @RequestHeader("Authorization") String authorizationHeader,
            @PathVariable("id") Long courseId,
            @PathVariable("sectionId") Long sectionId,
            @PathVariable("itemId") Long itemId,
            @Valid @RequestBody UpdateCourseContentItemRequest request
    ) {
        return courseService.updateContentItem(authorizationHeader, courseId, sectionId, itemId, request);
    }

    @PatchMapping("/{id}/sections/{sectionId}/items/{itemId}/reorder")
    public CourseContentItemResponse reorderContentItem(
            @RequestHeader("Authorization") String authorizationHeader,
            @PathVariable("id") Long courseId,
            @PathVariable("sectionId") Long sectionId,
            @PathVariable("itemId") Long itemId,
            @Valid @RequestBody ReorderCourseContentItemRequest request
    ) {
        return courseService.reorderContentItem(authorizationHeader, courseId, sectionId, itemId, request);
    }

    @DeleteMapping("/{id}/sections/{sectionId}/items/{itemId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteContentItem(
            @RequestHeader("Authorization") String authorizationHeader,
            @PathVariable("id") Long courseId,
            @PathVariable("sectionId") Long sectionId,
            @PathVariable("itemId") Long itemId
    ) {
        courseService.deleteContentItem(authorizationHeader, courseId, sectionId, itemId);
    }

    @PostMapping(value = "/{id}/content-files", consumes = "multipart/form-data")
    @ResponseStatus(HttpStatus.CREATED)
    public CourseContentFileResponse uploadContentPdf(
            @RequestHeader("Authorization") String authorizationHeader,
            @PathVariable("id") Long courseId,
            @RequestParam("file") MultipartFile file
    ) {
        return courseService.uploadContentPdf(authorizationHeader, courseId, file);
    }

    @PostMapping(value = "/{id}/content-videos", consumes = "multipart/form-data")
    @ResponseStatus(HttpStatus.CREATED)
    public CourseContentFileResponse uploadContentVideo(
            @RequestHeader("Authorization") String authorizationHeader,
            @PathVariable("id") Long courseId,
            @RequestParam("file") MultipartFile file
    ) {
        return courseService.uploadContentVideo(authorizationHeader, courseId, file);
    }
}
