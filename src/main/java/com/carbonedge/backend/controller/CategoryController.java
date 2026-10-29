package com.carbonedge.backend.controller;

import com.carbonedge.backend.dto.CategoryResponse;
import com.carbonedge.backend.dto.CreateCategoryRequest;
import com.carbonedge.backend.dto.UpdateCategoryRequest;
import com.carbonedge.backend.service.CategoryService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    @GetMapping
    public List<CategoryResponse> getCategories(@RequestHeader("Authorization") String authorizationHeader) {
        return categoryService.getCategories(authorizationHeader);
    }

    @GetMapping("/{id}")
    public CategoryResponse getCategoryById(
            @RequestHeader("Authorization") String authorizationHeader,
            @PathVariable("id") Long categoryId
    ) {
        return categoryService.getCategoryById(authorizationHeader, categoryId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CategoryResponse createCategory(
            @RequestHeader("Authorization") String authorizationHeader,
            @Valid @RequestBody CreateCategoryRequest request
    ) {
        return categoryService.createCategory(authorizationHeader, request);
    }

    @PatchMapping("/{id}")
    public CategoryResponse updateCategory(
            @RequestHeader("Authorization") String authorizationHeader,
            @PathVariable("id") Long categoryId,
            @Valid @RequestBody UpdateCategoryRequest request
    ) {
        return categoryService.updateCategory(authorizationHeader, categoryId, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCategory(
            @RequestHeader("Authorization") String authorizationHeader,
            @PathVariable("id") Long categoryId
    ) {
        categoryService.deleteCategory(authorizationHeader, categoryId);
    }
}
