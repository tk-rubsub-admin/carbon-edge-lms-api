package com.carbonedge.backend.service;

import com.carbonedge.backend.dto.SearchStudentsRequest;
import com.carbonedge.backend.dto.SearchStudentsResponse;
import com.carbonedge.backend.dto.UserProfileResponse;
import com.carbonedge.backend.exception.ForbiddenException;
import com.carbonedge.backend.exception.UnauthorizedException;
import com.carbonedge.backend.model.AuthSession;
import com.carbonedge.backend.model.MoodleUserMapping;
import com.carbonedge.backend.model.UserAccount;
import com.carbonedge.backend.model.UserRole;
import com.carbonedge.backend.repository.AuthSessionRepository;
import com.carbonedge.backend.repository.MoodleUserMappingRepository;
import com.carbonedge.backend.repository.UserAccountRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class StudentSearchService {

    private final AuthSessionRepository authSessionRepository;
    private final UserAccountRepository userAccountRepository;
    private final MoodleUserMappingRepository moodleUserMappingRepository;

    @Transactional(readOnly = true)
    public SearchStudentsResponse searchStudents(String authorizationHeader, SearchStudentsRequest request) {
        AuthSession session = resolveSession(authorizationHeader);
        requireAdmin(session);

        String keyword = request == null ? null : trimToNull(request.keyword());
        int page = request != null && request.page() != null ? request.page() : 1;
        int size = request != null && request.size() != null ? request.size() : 10;
        int safePage = Math.max(1, page) - 1;
        int safeSize = Math.min(Math.max(1, size), 100);

        var studentPage = userAccountRepository.searchByRoleAndKeyword(
                UserRole.STUDENT,
                keyword,
                PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.ASC, "username"))
        );

        List<UserProfileResponse> items = studentPage.getContent()
                .stream()
                .map(this::toProfileResponse)
                .toList();

        return new SearchStudentsResponse(
                items,
                studentPage.getNumber() + 1,
                studentPage.getSize(),
                studentPage.getTotalElements(),
                studentPage.getTotalPages()
        );
    }

    @Transactional
    public UserProfileResponse approveStudent(String authorizationHeader, Long studentId) {
        AuthSession session = resolveSession(authorizationHeader);
        requireAdmin(session);
        UserAccount student = userAccountRepository.findById(studentId)
                .orElseThrow(() -> new com.carbonedge.backend.exception.NotFoundException("Student not found"));
        if (student.getRole() != UserRole.STUDENT) {
            throw new com.carbonedge.backend.exception.ConflictException("Only student accounts can be approved");
        }
        student.setStatus(com.carbonedge.backend.model.UserStatus.ACTIVE);
        return toProfileResponse(userAccountRepository.save(student));
    }

    @Transactional
    public UserProfileResponse suspendStudent(String authorizationHeader, Long studentId) {
        AuthSession session = resolveSession(authorizationHeader);
        requireAdmin(session);
        UserAccount student = userAccountRepository.findById(studentId)
                .orElseThrow(() -> new com.carbonedge.backend.exception.NotFoundException("Student not found"));
        if (student.getRole() != UserRole.STUDENT) {
            throw new com.carbonedge.backend.exception.ConflictException("Only student accounts can be suspended");
        }
        student.setStatus(com.carbonedge.backend.model.UserStatus.INACTIVE);
        return toProfileResponse(userAccountRepository.save(student));
    }

    private UserProfileResponse toProfileResponse(UserAccount user) {
        Long moodleUserId = moodleUserMappingRepository.findByUser(user)
                .map(MoodleUserMapping::getMoodleUserId)
                .orElse(null);
        return UserProfileResponse.from(user, moodleUserId);
    }

    private AuthSession resolveSession(String authorizationHeader) {
        String token = extractBearerToken(authorizationHeader);
        AuthSession session = authSessionRepository.findByAccessToken(token)
                .orElseThrow(() -> new UnauthorizedException("Invalid access token"));
        if (!session.isActive()) {
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
