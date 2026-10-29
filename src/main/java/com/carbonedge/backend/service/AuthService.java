package com.carbonedge.backend.service;

import com.carbonedge.backend.dto.AuthResponse;
import com.carbonedge.backend.dto.AccountAvailabilityResponse;
import com.carbonedge.backend.dto.CourseLaunchResponse;
import com.carbonedge.backend.dto.EmailDispatchResponse;
import com.carbonedge.backend.dto.ForgotPasswordRequest;
import com.carbonedge.backend.dto.LoginRequest;
import com.carbonedge.backend.dto.MoodleSessionResponse;
import com.carbonedge.backend.dto.RegistrationResponse;
import com.carbonedge.backend.dto.RegisterRequest;
import com.carbonedge.backend.dto.ResetPasswordRequest;
import com.carbonedge.backend.dto.ResendVerificationRequest;
import com.carbonedge.backend.dto.UserProfileResponse;
import com.carbonedge.backend.dto.UpdateStudentProfileRequest;
import com.carbonedge.backend.exception.ConflictException;
import com.carbonedge.backend.exception.ForbiddenException;
import com.carbonedge.backend.exception.UnauthorizedException;
import com.carbonedge.backend.client.moodle.MoodleClient;
import com.carbonedge.backend.dto.moodle.MoodleCreateUserRequest;
import com.carbonedge.backend.dto.moodle.MoodleUpdateUserRequest;
import com.carbonedge.backend.dto.moodle.MoodleUserRecord;
import com.carbonedge.backend.model.AuthSession;
import com.carbonedge.backend.model.CourseStudentEnrollment;
import com.carbonedge.backend.model.MoodleUserMapping;
import com.carbonedge.backend.model.UserAccount;
import com.carbonedge.backend.model.UserStatus;
import com.carbonedge.backend.model.UserRole;
import com.carbonedge.backend.repository.AuthSessionRepository;
import com.carbonedge.backend.repository.CourseStudentEnrollmentRepository;
import com.carbonedge.backend.repository.MoodleUserMappingRepository;
import com.carbonedge.backend.repository.UserAccountRepository;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.time.Instant;
import java.util.Comparator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final String GENERATED_EMAIL_DOMAIN = "users.carbonedge.invalid";

    private final UserAccountRepository userAccountRepository;
    private final MoodleUserMappingRepository moodleUserMappingRepository;
    private final AuthSessionRepository authSessionRepository;
    private final CourseStudentEnrollmentRepository courseStudentEnrollmentRepository;
    private final PasswordEncoder passwordEncoder;
    private final MoodleClient moodleClient;
    private final MoodleLaunchService moodleLaunchService;
    private final EmailVerificationService emailVerificationService;
    private final PasswordResetService passwordResetService;
    private final SessionService sessionService;
    private final BotProtectionService botProtectionService;
    private final Validator validator;

    @Transactional
    public RegistrationResponse register(RegisterRequest request) {
        botProtectionService.validate(request);
        return createStudentAccount(request);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public RegistrationResponse createStudentForManagement(String username, String password) {
        RegisterRequest request = new RegisterRequest(
                username,
                null,
                password,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
        validator.validate(request)
                .stream()
                .sorted(Comparator.comparing(violation -> violation.getPropertyPath().toString()))
                .map(ConstraintViolation::getMessage)
                .findFirst()
                .ifPresent(message -> {
                    throw new IllegalArgumentException(message);
                });
        return createStudentAccount(request);
    }

    private RegistrationResponse createStudentAccount(RegisterRequest request) {
        String username = request.username().trim();
        boolean generatedEmail = request.email() == null || request.email().isBlank();
        String email = generatedEmail
                ? username.toLowerCase() + "@" + GENERATED_EMAIL_DOMAIN
                : request.email().trim().toLowerCase();
        String firstName = defaultDisplayName(request.firstName(), username);
        String lastName = defaultLastName(request.lastName());

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
                firstName,
                lastName
        ));

        UserAccount user = new UserAccount();
        user.setUsername(username);
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setTitle(trimToNull(request.title()));
        user.setPhoneNumber(trimToNull(request.phoneNumber()));
        user.setAddressHouseNumber(trimToNull(request.addressHouseNumber()));
        user.setAddressMoo(trimToNull(request.addressMoo()));
        user.setAddressBuilding(trimToNull(request.addressBuilding()));
        user.setAddressAlley(trimToNull(request.addressAlley()));
        user.setAddressRoad(trimToNull(request.addressRoad()));
        user.setAddressProvince(trimToNull(request.addressProvince()));
        user.setAddressDistrict(trimToNull(request.addressDistrict()));
        user.setAddressSubdistrict(trimToNull(request.addressSubdistrict()));
        user.setAddressPostalCode(trimToNull(request.addressPostalCode()));
        Instant registeredAt = Instant.now();
        user.setPdpaConsentAt(Boolean.TRUE.equals(request.pdpaConsent()) ? registeredAt : null);
        user.setInformationCertifiedAt(Boolean.TRUE.equals(request.informationCertified()) ? registeredAt : null);
        user.setRole(UserRole.STUDENT);
        user.setStatus(generatedEmail ? UserStatus.ACTIVE : UserStatus.PENDING_VERIFICATION);
        boolean completedApplicantProfile = hasCompletedApplicantProfile(request);
        user.setFirstLogin(!completedApplicantProfile);
        user.setCompletedProfile(completedApplicantProfile);
        userAccountRepository.save(user);

        MoodleUserMapping mapping = new MoodleUserMapping();
        mapping.setUser(user);
        mapping.setMoodleUserId(moodleUser.id());
        moodleUserMappingRepository.save(mapping);

//        enrollStudentInAllCourses(user, mapping);

        Instant verificationExpiresAt = generatedEmail
                ? null
                : emailVerificationService.issueVerificationEmail(user);
        return new RegistrationResponse(
                user.getId(),
                user.getEmail(),
                verificationExpiresAt,
                !generatedEmail,
                user.isFirstLogin(),
                user.isCompletedProfile()
        );
    }

    @Transactional(readOnly = true)
    public AccountAvailabilityResponse availability(String username, String email) {
        boolean usernameAvailable = username == null || username.isBlank()
                || !userAccountRepository.existsByUsernameIgnoreCase(username.trim());
        boolean emailAvailable = email == null || email.isBlank()
                || !userAccountRepository.existsByEmailIgnoreCase(email.trim().toLowerCase());
        return new AccountAvailabilityResponse(usernameAvailable, emailAvailable);
    }

    private String defaultDisplayName(String value, String username) {
        return value == null || value.isBlank() ? username : value.trim();
    }

    private String defaultLastName(String value) {
        return value == null || value.isBlank() ? "-" : value.trim();
    }

    private boolean hasCompletedApplicantProfile(RegisterRequest request) {
        return trimToNull(request.firstName()) != null
                && trimToNull(request.lastName()) != null
                && trimToNull(request.phoneNumber()) != null
                && trimToNull(request.addressHouseNumber()) != null
                && trimToNull(request.addressProvince()) != null
                && trimToNull(request.addressDistrict()) != null
                && trimToNull(request.addressSubdistrict()) != null
                && trimToNull(request.addressPostalCode()) != null
                && Boolean.TRUE.equals(request.pdpaConsent())
                && Boolean.TRUE.equals(request.informationCertified());
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim();
    }

    private void enrollStudentInAllCourses(UserAccount user, MoodleUserMapping mapping) {
        moodleClient.getCourses().forEach(course -> {
            Long courseId = course.id();
            // Moodle course id 1 is the site home course. It is not a regular
            // enrolment target and normally has no manual-enrolment instance.
            if (courseId == null || courseId == 1L
                    || courseStudentEnrollmentRepository.existsByUserIdAndCourseId(user.getId(), courseId)) {
                return;
            }

            moodleClient.enrollStudent(courseId, mapping.getMoodleUserId());

            CourseStudentEnrollment enrollment = new CourseStudentEnrollment();
            enrollment.setCourseId(courseId);
            enrollment.setUser(user);
            courseStudentEnrollmentRepository.save(enrollment);
        });
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
            throw new UnauthorizedException("Email is not verified");
        }

        log.info("Moodle login attempt for user='{}' len={}",
                user.getUsername(),
                user.getUsername() == null ? 0 : user.getUsername().length());

        moodleClient.validateCredentials(user.getUsername(), request.password());

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            user.setPasswordHash(passwordEncoder.encode(request.password()));
            userAccountRepository.save(user);
            log.info("Updated local password hash after successful Moodle login for userId={}", user.getId());
        }

        MoodleUserMapping mapping = moodleUserMappingRepository.findByUser(user)
                .orElseThrow(() -> new UnauthorizedException("User has no Moodle mapping"));

        return toAuthResponse(sessionService.create(user), user, mapping);
    }

    @Transactional
    public AuthResponse verifyEmail(String rawToken) {
        UserAccount user = emailVerificationService.verifyToken(rawToken);
        MoodleUserMapping mapping = moodleUserMappingRepository.findByUser(user)
                .orElseThrow(() -> new UnauthorizedException("User has no Moodle mapping"));
        return toAuthResponse(sessionService.create(user), user, mapping);
    }

    @Transactional
    public EmailDispatchResponse resendVerificationEmail(ResendVerificationRequest request) {
        return userAccountRepository.findByEmailIgnoreCase(request.email().trim().toLowerCase())
                .filter(user -> user.getStatus() != UserStatus.ACTIVE)
                .map(user -> new EmailDispatchResponse(
                        true,
                        emailVerificationService.resendVerificationEmail(user)
                ))
                .orElse(new EmailDispatchResponse(false, null));
    }

    @Transactional
    public EmailDispatchResponse forgotPassword(ForgotPasswordRequest request) {
        return userAccountRepository.findByEmailIgnoreCase(request.email().trim().toLowerCase())
                .filter(user -> user.getStatus() == UserStatus.ACTIVE)
                .map(user -> new EmailDispatchResponse(
                        true,
                        passwordResetService.issueResetEmail(user)
                ))
                .orElse(new EmailDispatchResponse(false, null));
    }

    @Transactional
    public AuthResponse resetPassword(ResetPasswordRequest request) {
        UserAccount user = passwordResetService.resetPassword(
                request.token().trim(),
                request.newPassword().trim()
        );
        sessionService.revokeAllForUser(user);
        MoodleUserMapping mapping = moodleUserMappingRepository.findByUser(user)
                .orElseThrow(() -> new UnauthorizedException("User has no Moodle mapping"));
        return toAuthResponse(sessionService.create(user), user, mapping);
    }

    @Transactional
    public AuthResponse refresh(String refreshToken) {
        IssuedSession issued = sessionService.refresh(refreshToken.trim());
        UserAccount user = issued.session().getUser();
        MoodleUserMapping mapping = moodleUserMappingRepository.findByUser(user)
                .orElseThrow(() -> new UnauthorizedException("User has no Moodle mapping"));
        return toAuthResponse(issued, user, mapping);
    }

    @Transactional
    public void logout(String authorizationHeader) {
        sessionService.revokeByAccessToken(extractBearerToken(authorizationHeader));
    }

    @Transactional(readOnly = true)
    public UserProfileResponse currentUser(String authorizationHeader) {
        AuthSession session = resolveSession(authorizationHeader);
        Long moodleUserId = moodleUserMappingRepository.findByUser(session.getUser())
                .map(MoodleUserMapping::getMoodleUserId)
                .orElse(null);
        return UserProfileResponse.from(session.getUser(), moodleUserId);
    }

    @Transactional
    public UserProfileResponse updateStudentProfile(
            String authorizationHeader,
            UpdateStudentProfileRequest request
    ) {
        UserAccount user = resolveSession(authorizationHeader).getUser();
        if (user.getRole() != UserRole.STUDENT) {
            throw new ForbiddenException("Only students can update their profile");
        }

        String email = request.email().trim().toLowerCase();
        String firstName = request.firstName().trim();
        String lastName = request.lastName().trim();
        if (userAccountRepository.existsByEmailIgnoreCaseAndIdNot(email, user.getId())) {
            throw new ConflictException("Email already exists");
        }

        MoodleUserMapping mapping = moodleUserMappingRepository.findByUser(user)
                .orElseThrow(() -> new UnauthorizedException("User has no Moodle mapping"));
        moodleClient.updateUser(new MoodleUpdateUserRequest(
                mapping.getMoodleUserId(),
                email,
                firstName,
                lastName
        ));

        user.setEmail(email);
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setFirstLogin(false);
        user.setCompletedProfile(true);
        userAccountRepository.save(user);

        return UserProfileResponse.from(user, mapping.getMoodleUserId());
    }

    @Transactional
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

    private AuthResponse toAuthResponse(IssuedSession issued, UserAccount user, MoodleUserMapping mapping) {
        AuthSession session = issued.session();
        return new AuthResponse(
                session.getAccessToken(),
                session.getExpiresAt(),
                issued.refreshToken(),
                session.getRefreshExpiresAt(),
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
