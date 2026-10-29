package com.carbonedge.backend.service;

import com.carbonedge.backend.client.moodle.MoodleClient;
import com.carbonedge.backend.dto.CategoryResponse;
import com.carbonedge.backend.dto.CreateCategoryRequest;
import com.carbonedge.backend.dto.UpdateCategoryRequest;
import com.carbonedge.backend.dto.moodle.MoodleCreateCategoryRequest;
import com.carbonedge.backend.dto.moodle.MoodleUpdateCategoryRequest;
import com.carbonedge.backend.exception.ForbiddenException;
import com.carbonedge.backend.exception.NotFoundException;
import com.carbonedge.backend.exception.UnauthorizedException;
import com.carbonedge.backend.model.AuthSession;
import com.carbonedge.backend.model.UserRole;
import com.carbonedge.backend.repository.AuthSessionRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CategoryService {

    private final AuthSessionRepository authSessionRepository;
    private final MoodleClient moodleClient;

    public CategoryService(AuthSessionRepository authSessionRepository, MoodleClient moodleClient) {
        this.authSessionRepository = authSessionRepository;
        this.moodleClient = moodleClient;
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> getCategories(String authorizationHeader) {
        AuthSession session = resolveSession(authorizationHeader);
        requireAdmin(session);
        return moodleClient.getCategories().stream()
                .map(CategoryResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public CategoryResponse getCategoryById(String authorizationHeader, Long categoryId) {
        AuthSession session = resolveSession(authorizationHeader);
        requireAdmin(session);
        return CategoryResponse.from(moodleClient.getCategoryById(categoryId));
    }

    @Transactional
    public CategoryResponse createCategory(String authorizationHeader, CreateCategoryRequest request) {
        AuthSession session = resolveSession(authorizationHeader);
        requireAdmin(session);

        MoodleCreateCategoryRequest moodleRequest = new MoodleCreateCategoryRequest(
                request.name().trim(),
                request.parent().longValue(),
                trimToNull(request.idNumber()),
                trimToNull(request.description()),
                request.visible() == null || request.visible(),
                trimToNull(request.theme())
        );
        return CategoryResponse.from(moodleClient.createCategory(moodleRequest));
    }

    @Transactional
    public CategoryResponse updateCategory(String authorizationHeader, Long categoryId, UpdateCategoryRequest request) {
        AuthSession session = resolveSession(authorizationHeader);
        requireAdmin(session);
        moodleClient.getCategoryById(categoryId);

        if (request.name() == null
                && request.parent() == null
                && request.idNumber() == null
                && request.description() == null
                && request.visible() == null
                && request.theme() == null) {
            throw new IllegalArgumentException("At least one category field must be provided");
        }

        MoodleUpdateCategoryRequest moodleRequest = new MoodleUpdateCategoryRequest(
                categoryId,
                trimToNull(request.name()),
                request.parent() == null ? null : request.parent().longValue(),
                trimToNull(request.idNumber()),
                trimToNull(request.description()),
                request.visible(),
                trimToNull(request.theme())
        );
        return CategoryResponse.from(moodleClient.updateCategory(moodleRequest));
    }

    @Transactional
    public void deleteCategory(String authorizationHeader, Long categoryId) {
        AuthSession session = resolveSession(authorizationHeader);
        requireAdmin(session);
        CategoryResponse category = CategoryResponse.from(moodleClient.getCategoryById(categoryId));

        boolean hasCourses = (category.courseCount() != null && category.courseCount() > 0);
        boolean hasChildren = moodleClient.getCategories().stream()
                .anyMatch(child -> child.parent() != null && child.parent().equals(categoryId));
        if (hasCourses || hasChildren) {
            throw new IllegalArgumentException("Category must be empty before deleting");
        }

        moodleClient.deleteCategory(categoryId);
    }

    private AuthSession resolveSession(String authorizationHeader) {
        String token = extractBearerToken(authorizationHeader);
        AuthSession session = authSessionRepository.findByAccessToken(token)
                .orElseThrow(() -> new UnauthorizedException("Invalid access token"));
        if (session.getRevokedAt() != null || !session.getExpiresAt().isAfter(Instant.now())) {
            throw new UnauthorizedException("Session has expired");
        }
        return session;
    }

    private void requireAdmin(AuthSession session) {
        if (session.getUser().getRole() != UserRole.ADMIN) {
            throw new ForbiddenException("Admin access is required");
        }
    }

    private String extractBearerToken(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            throw new UnauthorizedException("Missing bearer token");
        }
        return authorizationHeader.substring(7).trim();
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
