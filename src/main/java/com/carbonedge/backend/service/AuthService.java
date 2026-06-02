package com.carbonedge.backend.service;

import com.carbonedge.backend.dto.AuthResponse;
import com.carbonedge.backend.dto.CourseLaunchResponse;
import com.carbonedge.backend.dto.LoginRequest;
import com.carbonedge.backend.dto.MoodleSessionResponse;
import com.carbonedge.backend.dto.RegisterRequest;
import com.carbonedge.backend.dto.UserProfileResponse;
import com.carbonedge.backend.exception.ConflictException;
import com.carbonedge.backend.exception.UnauthorizedException;
import com.carbonedge.backend.client.moodle.MoodleClient;
import com.carbonedge.backend.dto.moodle.MoodleCreateUserRequest;
import com.carbonedge.backend.dto.moodle.MoodleUserRecord;
import com.carbonedge.backend.model.AuthSession;
import com.carbonedge.backend.model.MoodleUserMapping;
import com.carbonedge.backend.model.UserAccount;
import com.carbonedge.backend.model.UserStatus;
import com.carbonedge.backend.repository.AuthSessionRepository;
import com.carbonedge.backend.repository.MoodleUserMappingRepository;
import com.carbonedge.backend.repository.UserAccountRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final Duration SESSION_TTL = Duration.ofHours(8);

    private final UserAccountRepository userAccountRepository;
    private final MoodleUserMappingRepository moodleUserMappingRepository;
    private final AuthSessionRepository authSessionRepository;
    private final PasswordEncoder passwordEncoder;
    private final MoodleClient moodleClient;
    private final MoodleLaunchService moodleLaunchService;
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String username = request.username().trim();
        String email = request.email().trim().toLowerCase();

        if (userAccountRepository.existsByUsernameIgnoreCase(username)) {
            throw new ConflictException("Username already exists");
        }
        if (userAccountRepository.existsByEmailIgnoreCase(email)) {
            throw new ConflictException("Email already exists");
        }

        MoodleUserRecord moodleUser = moodleClient.createUser(new MoodleCreateUserRequest(
                username,
                email,
                request.password(),
                request.firstName().trim(),
                request.lastName().trim()
        ));

        UserAccount user = new UserAccount();
        user.setUsername(username);
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setFirstName(request.firstName().trim());
        user.setLastName(request.lastName().trim());
        user.setStatus(UserStatus.ACTIVE);
        userAccountRepository.save(user);

        MoodleUserMapping mapping = new MoodleUserMapping();
        mapping.setUser(user);
        mapping.setMoodleUserId(moodleUser.id());
        moodleUserMappingRepository.save(mapping);

        AuthSession session = createSession(user);
        return toAuthResponse(session, user, mapping);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        String usernameOrEmail = request.usernameOrEmail().trim();

        UserAccount user = userAccountRepository.findByUsernameIgnoreCaseOrEmailIgnoreCase(
                        usernameOrEmail,
                        usernameOrEmail
                )
                .orElseThrow(() -> {
                    log.warn("Login rejected: local user not found for identifier={}", usernameOrEmail);
                    return new UnauthorizedException("Invalid credentials");
                });

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new UnauthorizedException("User is not active");
        }

        moodleClient.validateCredentials(user.getUsername(), request.password());

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            user.setPasswordHash(passwordEncoder.encode(request.password()));
            userAccountRepository.save(user);
            log.info("Updated local password hash after successful Moodle login for userId={}", user.getId());
        }

        MoodleUserMapping mapping = moodleUserMappingRepository.findByUser(user)
                .orElseThrow(() -> new UnauthorizedException("User has no Moodle mapping"));

        AuthSession session = createSession(user);
        return toAuthResponse(session, user, mapping);
    }

    @Transactional(readOnly = true)
    public UserProfileResponse currentUser(String authorizationHeader) {
        AuthSession session = resolveSession(authorizationHeader);
        Long moodleUserId = moodleUserMappingRepository.findByUser(session.getUser())
                .map(MoodleUserMapping::getMoodleUserId)
                .orElse(null);
        return UserProfileResponse.from(session.getUser(), moodleUserId);
    }

    @Transactional(readOnly = true)
    public MoodleSessionResponse createMoodleSession(String authorizationHeader) {
        AuthSession session = resolveSession(authorizationHeader);
        MoodleUserMapping mapping = moodleUserMappingRepository.findByUser(session.getUser())
                .orElseThrow(() -> new UnauthorizedException("User has no Moodle mapping"));
        CourseLaunchResponse launch = moodleLaunchService.issueLaunchUrl(
                session.getUser(),
                mapping.getMoodleUserId(),
                null,
                "/my/"
        );
        return new MoodleSessionResponse(launch.launchUrl());
    }

    private AuthSession createSession(UserAccount user) {
        AuthSession session = new AuthSession();
        session.setUser(user);
        session.setAccessToken(UUID.randomUUID().toString());
        session.setExpiresAt(Instant.now().plus(SESSION_TTL));
        return authSessionRepository.save(session);
    }

    private AuthResponse toAuthResponse(AuthSession session, UserAccount user, MoodleUserMapping mapping) {
        return new AuthResponse(
                session.getAccessToken(),
                session.getExpiresAt(),
                UserProfileResponse.from(user, mapping.getMoodleUserId())
        );
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

    private String extractBearerToken(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            throw new UnauthorizedException("Missing bearer token");
        }
        return authorizationHeader.substring(7).trim();
    }
}
