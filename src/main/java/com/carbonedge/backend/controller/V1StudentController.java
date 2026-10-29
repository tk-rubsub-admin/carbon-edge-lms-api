package com.carbonedge.backend.controller;

import com.carbonedge.backend.dto.SearchStudentsRequest;
import com.carbonedge.backend.dto.SearchStudentsResponse;
import com.carbonedge.backend.dto.BulkStudentCreationResponse;
import com.carbonedge.backend.dto.UserProfileResponse;
import com.carbonedge.backend.service.StudentSearchService;
import com.carbonedge.backend.service.StudentBulkImportService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.MediaType;

@RestController
@RequestMapping("/v1/students")
@RequiredArgsConstructor
public class V1StudentController {

    private final StudentSearchService studentSearchService;
    private final StudentBulkImportService studentBulkImportService;

    @PostMapping
    public SearchStudentsResponse searchStudents(
            @RequestHeader("Authorization") String authorizationHeader,
            @Valid @RequestBody(required = false) SearchStudentsRequest request
    ) {
        return studentSearchService.searchStudents(authorizationHeader, request);
    }

    @PatchMapping("/{studentId}/approve")
    public UserProfileResponse approveStudent(
            @RequestHeader("Authorization") String authorizationHeader,
            @PathVariable Long studentId
    ) {
        return studentSearchService.approveStudent(authorizationHeader, studentId);
    }

    @PatchMapping("/{studentId}/suspend")
    public UserProfileResponse suspendStudent(
            @RequestHeader("Authorization") String authorizationHeader,
            @PathVariable Long studentId
    ) {
        return studentSearchService.suspendStudent(authorizationHeader, studentId);
    }

    @PostMapping(value = "/bulk-upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public BulkStudentCreationResponse bulkCreateStudents(
            @RequestHeader("Authorization") String authorizationHeader,
            @RequestParam("file") MultipartFile file
    ) {
        return studentBulkImportService.bulkCreateStudents(authorizationHeader, file);
    }
}
