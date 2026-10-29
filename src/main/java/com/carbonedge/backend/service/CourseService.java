package com.carbonedge.backend.service;

import com.carbonedge.backend.client.moodle.MoodleClient;
import com.carbonedge.backend.dto.CreateCourseRequest;
import com.carbonedge.backend.dto.CreateCourseTemplateRequest;
import com.carbonedge.backend.dto.CourseContentItemResponse;
import com.carbonedge.backend.dto.CourseDetailsResponse;
import com.carbonedge.backend.dto.MoodleCourseContentResponse;
import com.carbonedge.backend.dto.SearchCoursesRequest;
import com.carbonedge.backend.dto.CourseTemplateResponse;
import com.carbonedge.backend.dto.CourseSectionResponse;
import com.carbonedge.backend.dto.CourseTeacherResponse;
import com.carbonedge.backend.dto.CourseStudentResponse;
import com.carbonedge.backend.dto.CourseStudentPageResponse;
import com.carbonedge.backend.dto.BulkStudentEnrollmentResponse;
import com.carbonedge.backend.dto.BulkStudentEnrollmentRowResponse;
import com.carbonedge.backend.dto.CreateCourseContentItemRequest;
import com.carbonedge.backend.dto.CreateCourseSectionRequest;
import com.carbonedge.backend.dto.DuplicateCourseRequest;
import com.carbonedge.backend.dto.UpdateCourseRequest;
import com.carbonedge.backend.dto.UpdateCourseContentItemRequest;
import com.carbonedge.backend.dto.UpdateCourseSectionRequest;
import com.carbonedge.backend.dto.UpdateCourseTemplateRequest;
import com.carbonedge.backend.dto.CourseLaunchResponse;
import com.carbonedge.backend.dto.CourseContentFileResponse;
import com.carbonedge.backend.dto.CourseResponse;
import com.carbonedge.backend.dto.ReorderCourseContentItemRequest;
import com.carbonedge.backend.dto.ReorderCourseSectionRequest;
import com.carbonedge.backend.exception.ConflictException;
import com.carbonedge.backend.exception.NotFoundException;
import com.carbonedge.backend.exception.ForbiddenException;
import com.carbonedge.backend.exception.IntegrationException;
import com.carbonedge.backend.exception.UnauthorizedException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.carbonedge.backend.model.CourseTemplate;
import com.carbonedge.backend.model.CourseTemplateType;
import com.carbonedge.backend.model.AuthSession;
import com.carbonedge.backend.model.CourseContentItem;
import com.carbonedge.backend.model.CourseContentType;
import com.carbonedge.backend.model.CourseSection;
import com.carbonedge.backend.model.CourseTeacherAssignment;
import com.carbonedge.backend.model.CourseStudentEnrollment;
import com.carbonedge.backend.dto.moodle.MoodleCreateCourseRequest;
import com.carbonedge.backend.dto.moodle.MoodleCourseRecord;
import com.carbonedge.backend.dto.moodle.MoodleUpdateCourseRequest;
import com.carbonedge.backend.model.MoodleUserMapping;
import com.carbonedge.backend.model.UserAccount;
import com.carbonedge.backend.model.UserRole;
import com.carbonedge.backend.repository.AuthSessionRepository;
import com.carbonedge.backend.repository.CourseContentItemRepository;
import com.carbonedge.backend.repository.CourseContentProgressRepository;
import com.carbonedge.backend.repository.CourseSectionRepository;
import com.carbonedge.backend.repository.CourseTemplateRepository;
import com.carbonedge.backend.repository.CourseTeacherAssignmentRepository;
import com.carbonedge.backend.repository.CourseStudentEnrollmentRepository;
import com.carbonedge.backend.repository.MoodleUserMappingRepository;
import com.carbonedge.backend.repository.UserAccountRepository;
import java.time.Instant;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.Locale;
import org.apache.poi.EncryptedDocumentException;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class CourseService {

    private static final long MAX_STUDENT_IMPORT_FILE_SIZE = 5L * 1024 * 1024;
    private static final int MAX_STUDENT_IMPORT_ROWS = 1_000;

    private final AuthSessionRepository authSessionRepository;
    private final MoodleUserMappingRepository moodleUserMappingRepository;
    private final CourseTeacherAssignmentRepository courseTeacherAssignmentRepository;
    private final CourseStudentEnrollmentRepository courseStudentEnrollmentRepository;
    private final CourseSectionRepository courseSectionRepository;
    private final CourseContentItemRepository courseContentItemRepository;
    private final CourseContentProgressRepository courseContentProgressRepository;
    private final CourseTemplateRepository courseTemplateRepository;
    private final UserAccountRepository userAccountRepository;
    private final ObjectMapper objectMapper;
    private final MoodleClient moodleClient;
    private final MoodleLaunchService moodleLaunchService;
    private final SystemSettingsService systemSettingsService;
    private final CourseContentAccessService courseContentAccessService;
    private final CourseEnrollmentProgressService courseEnrollmentProgressService;
    private final SatisfactionSurveyService satisfactionSurveyService;

    @Value("${app.content-storage-path}")
    private String contentStoragePath;

    @Value("${app.public-base-url}")
    private String publicBaseUrl;

    public CourseService(
            AuthSessionRepository authSessionRepository,
            MoodleUserMappingRepository moodleUserMappingRepository,
            CourseTeacherAssignmentRepository courseTeacherAssignmentRepository,
            CourseStudentEnrollmentRepository courseStudentEnrollmentRepository,
            CourseSectionRepository courseSectionRepository,
            CourseContentItemRepository courseContentItemRepository,
            CourseContentProgressRepository courseContentProgressRepository,
            CourseTemplateRepository courseTemplateRepository,
            UserAccountRepository userAccountRepository,
            ObjectMapper objectMapper,
            MoodleClient moodleClient,
            MoodleLaunchService moodleLaunchService,
            SystemSettingsService systemSettingsService,
            CourseContentAccessService courseContentAccessService,
            CourseEnrollmentProgressService courseEnrollmentProgressService,
            SatisfactionSurveyService satisfactionSurveyService
    ) {
        this.authSessionRepository = authSessionRepository;
        this.moodleUserMappingRepository = moodleUserMappingRepository;
        this.courseTeacherAssignmentRepository = courseTeacherAssignmentRepository;
        this.courseStudentEnrollmentRepository = courseStudentEnrollmentRepository;
        this.courseSectionRepository = courseSectionRepository;
        this.courseContentItemRepository = courseContentItemRepository;
        this.courseContentProgressRepository = courseContentProgressRepository;
        this.courseTemplateRepository = courseTemplateRepository;
        this.userAccountRepository = userAccountRepository;
        this.objectMapper = objectMapper;
        this.moodleClient = moodleClient;
        this.moodleLaunchService = moodleLaunchService;
        this.systemSettingsService = systemSettingsService;
        this.courseContentAccessService = courseContentAccessService;
        this.courseEnrollmentProgressService = courseEnrollmentProgressService;
        this.satisfactionSurveyService = satisfactionSurveyService;
    }

    @Transactional
    public List<CourseResponse> getMyCourses(String authorizationHeader) {
        AuthSession session = resolveSession(authorizationHeader);
        return getAccessibleCourses(session);
    }

    @Transactional
    public List<CourseResponse> searchSystemCourses(String authorizationHeader, SearchCoursesRequest request) {
        AuthSession session = resolveSession(authorizationHeader);
        requireAdmin(session);

        String keyword = request == null ? null : trimToNull(request.keyword());
        List<MoodleCourseRecord> moodleCourses = moodleClient.getCourses();
        moodleCourses.forEach(course -> satisfactionSurveyService.ensureDefaultSurvey(course.id()));
        return moodleCourses.stream()
                .map(course -> CourseResponse.from(course)
                        .withStudentCount(Math.toIntExact(courseStudentEnrollmentRepository.countByCourseId(course.id()))))
                .filter(course -> matchesCourseKeyword(course, keyword))
                .sorted((left, right) -> {
                    int shortNameCompare = compareNullable(left.shortName(), right.shortName());
                    if (shortNameCompare != 0) {
                        return shortNameCompare;
                    }
                    return Long.compare(left.id(), right.id());
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public CourseResponse getMyCourseById(String authorizationHeader, Long courseId) {
        AuthSession session = resolveSession(authorizationHeader);
        requireCanAccessCourse(session, courseId);
        return CourseResponse.from(loadCourse(courseId));
    }

    @Transactional(readOnly = true)
    public List<CourseTeacherResponse> getCourseTeachers(String authorizationHeader, Long courseId) {
        AuthSession session = resolveSession(authorizationHeader);
        requireCanEditCourse(session, courseId);
        loadCourse(courseId);
        return courseTeacherAssignmentRepository.findAllByCourseId(courseId)
                .stream()
                .map(CourseTeacherResponse::from)
                .toList();
    }

    @Transactional
    public CourseStudentPageResponse getCourseStudents(
            String authorizationHeader,
            Long courseId,
            Integer page,
            Integer size
    ) {
        AuthSession session = resolveSession(authorizationHeader);
        requireCanEditCourse(session, courseId);
        loadCourse(courseId);

        int safePage = Math.max(1, page == null ? 1 : page) - 1;
        int safeSize = Math.min(Math.max(1, size == null ? 10 : size), 100);
        var studentPage = courseStudentEnrollmentRepository.findByCourseId(
                courseId,
                PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"))
        );
        List<CourseContentItem> requiredItems = courseContentItemRepository
                .findAllBySection_CourseIdOrderBySection_OrderIndexAscOrderIndexAscIdAsc(courseId)
                .stream()
                .filter(item -> item.isVisible() && item.isRequired())
                .toList();

        return new CourseStudentPageResponse(
                studentPage.getContent().stream()
                .map(enrollment -> courseEnrollmentProgressService
                        .refreshCompletion(enrollment.getUser(), courseId)
                        .orElse(enrollment))
                .map(enrollment -> {
                    Set<Long> completedItemIds = courseContentProgressRepository
                            .findAllByUserIdAndCourseId(enrollment.getUser().getId(), courseId)
                            .stream()
                            .filter(progress -> progress.getCompletedAt() != null)
                            .map(progress -> progress.getContentItem().getId())
                            .collect(Collectors.toSet());
                    int completedRequiredItems = (int) requiredItems.stream()
                            .filter(item -> completedItemIds.contains(item.getId()))
                            .count();
                    return CourseStudentResponse.from(
                            enrollment,
                            completedRequiredItems,
                            requiredItems.size()
                    );
                })
                .toList(),
                studentPage.getNumber() + 1,
                studentPage.getSize(),
                studentPage.getTotalElements(),
                studentPage.getTotalPages()
        );
    }

    @Transactional(readOnly = true)
    public List<Long> getCourseStudentIds(String authorizationHeader, Long courseId) {
        AuthSession session = resolveSession(authorizationHeader);
        requireCanEditCourse(session, courseId);
        loadCourse(courseId);
        return courseStudentEnrollmentRepository.findUserIdsByCourseId(courseId);
    }

    @Transactional
    public CourseLaunchResponse getMyCourseLaunch(String authorizationHeader, Long courseId) {
        AuthSession session = resolveSession(authorizationHeader);
        requireCanAccessCourse(session, courseId);
        CourseResponse course = CourseResponse.from(loadCourse(courseId));
        MoodleUserMapping mapping = moodleUserMappingRepository.findByUser(session.getUser())
                .orElseThrow(() -> new UnauthorizedException("User has no Moodle mapping"));
        return moodleLaunchService.issueLaunchUrl(
                session.getUser(),
                mapping.getMoodleUserId(),
                course.id(),
                "/course/view.php?id=" + course.id()
        );
    }

    @Transactional(readOnly = true)
    public CourseDetailsResponse getCourseDetails(String authorizationHeader, Long courseId) {
        AuthSession session = resolveSession(authorizationHeader);
        requireCanAccessCourse(session, courseId);
        return buildCourseDetails(courseId);
    }

    @Transactional(readOnly = true)
    public List<CourseSectionResponse> getCourseContent(String authorizationHeader, Long courseId) {
        AuthSession session = resolveSession(authorizationHeader);
        requireCanAccessCourse(session, courseId);
        return loadCourseSections(courseId);
    }

    @Transactional
    public MoodleCourseContentResponse getMoodleCourseContent(Long courseId, String pluginSecretHeader, Long moodleUserId) {
        moodleLaunchService.requirePluginSecret(pluginSecretHeader);
        var course = loadCourse(courseId);
        List<CourseSectionResponse> sections = loadCourseSections(courseId);
        String enrollmentStatus = null;
        Instant courseStartedAt = null;
        Instant courseCompletedAt = null;
        if (moodleUserId != null) {
            UserAccount user = moodleUserMappingRepository.findByMoodleUserId(moodleUserId)
                    .orElseThrow(() -> new NotFoundException("Moodle user mapping was not found"))
                    .getUser();
            var enrollment = courseEnrollmentProgressService.refreshCompletion(user, courseId).orElse(null);
            if (enrollment != null) {
                enrollmentStatus = enrollment.getStatus().name();
                courseStartedAt = enrollment.getStartedAt();
                courseCompletedAt = enrollment.getCompletedAt();
            }
            Set<Long> completedItemIds = courseContentProgressRepository.findAllByUserIdAndCourseId(user.getId(), courseId)
                    .stream()
                    .filter(progress -> progress.getCompletedAt() != null)
                    .map(progress -> progress.getContentItem().getId())
                    .collect(Collectors.toSet());
            sections = sections.stream()
                    .map(section -> new CourseSectionResponse(
                            section.id(), section.courseId(), section.title(), section.description(), section.orderIndex(),
                            section.visible(), section.locked(), section.items().stream()
                                    .map(item -> new CourseContentItemResponse(
                                            item.id(), item.sectionId(), item.type(), item.renderKind(), item.title(),
                                            item.description(), item.orderIndex(), item.required(), item.visible(),
                                            item.durationSeconds(), item.sourceUrl(), item.payloadJson(), item.publishedAt(),
                                            completedItemIds.contains(item.id()),
                                            courseContentAccessService.isLocked(user, courseId, item.id())))
                                    .toList()))
                    .toList();
        }
        long itemCount = sections.stream().mapToLong(section -> section.items().size()).sum();

        return new MoodleCourseContentResponse(
                course.id(),
                course.shortName(),
                course.fullName(),
                course.displayName(),
                course.summary(),
                sections.size(),
                Math.toIntExact(itemCount),
                enrollmentStatus,
                courseStartedAt,
                courseCompletedAt,
                sections
        );
    }

    @Transactional
    public CourseResponse createCourse(String authorizationHeader, CreateCourseRequest request) {
        AuthSession session = resolveSession(authorizationHeader);
        requireAdmin(session);

        MoodleCreateCourseRequest moodleRequest = new MoodleCreateCourseRequest(
                request.shortName().trim(),
                request.fullName().trim(),
                request.categoryId(),
                request.summary() == null ? null : request.summary().trim(),
                request.visible() == null || request.visible()
        );

        var createdCourse = moodleClient.createCourse(moodleRequest);
        satisfactionSurveyService.ensureDefaultSurvey(createdCourse.id());
        return CourseResponse.from(createdCourse);
    }

    @Transactional
    public CourseTemplateResponse createCourseTemplate(String authorizationHeader, CreateCourseTemplateRequest request) {
        AuthSession session = resolveSession(authorizationHeader);
        requireAdmin(session);

        Long sourceCourseId = request.sourceCourseId();
        loadCourse(sourceCourseId);

        String code = requireText(request.code(), "Template code is required");
        if (courseTemplateRepository.existsByCodeIgnoreCase(code)) {
            throw new ConflictException("Course template code already exists");
        }

        CourseTemplate template = new CourseTemplate();
        template.setCode(code);
        template.setName(requireText(request.name(), "Template name is required"));
        template.setDescription(trimToNull(request.description()));
        template.setTemplateType(request.templateType());
        template.setSourceCourseId(sourceCourseId);
        template.setCategoryIdDefault(request.categoryIdDefault());
        template.setVisibleDefault(request.visibleDefault() == null || request.visibleDefault());
        template.setActive(request.active() == null || request.active());
        template.setDefinitionJson(buildCourseTemplateDefinition(sourceCourseId));
        return CourseTemplateResponse.from(courseTemplateRepository.save(template));
    }

    @Transactional(readOnly = true)
    public List<CourseTemplateResponse> getCourseTemplates(String authorizationHeader) {
        AuthSession session = resolveSession(authorizationHeader);
        requireAdmin(session);
        return courseTemplateRepository.findAll()
                .stream()
                .map(CourseTemplateResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public CourseTemplateResponse getCourseTemplate(String authorizationHeader, Long templateId) {
        AuthSession session = resolveSession(authorizationHeader);
        requireAdmin(session);
        return CourseTemplateResponse.from(loadCourseTemplate(templateId));
    }

    @Transactional
    public CourseTemplateResponse updateCourseTemplate(
            String authorizationHeader,
            Long templateId,
            UpdateCourseTemplateRequest request
    ) {
        AuthSession session = resolveSession(authorizationHeader);
        requireAdmin(session);
        CourseTemplate template = loadCourseTemplate(templateId);

        if (request.code() != null) {
            String code = requireText(request.code(), "Template code is required");
            boolean codeChanged = !template.getCode().equalsIgnoreCase(code);
            if (codeChanged && courseTemplateRepository.existsByCodeIgnoreCase(code)) {
                throw new ConflictException("Course template code already exists");
            }
            template.setCode(code);
        }
        if (request.name() != null) {
            template.setName(requireText(request.name(), "Template name is required"));
        }
        if (request.description() != null) {
            template.setDescription(trimToNull(request.description()));
        }
        if (request.categoryIdDefault() != null) {
            template.setCategoryIdDefault(request.categoryIdDefault());
        }
        if (request.visibleDefault() != null) {
            template.setVisibleDefault(request.visibleDefault());
        }
        if (request.active() != null) {
            template.setActive(request.active());
        }
        return CourseTemplateResponse.from(courseTemplateRepository.save(template));
    }

    @Transactional
    public void deleteCourseTemplate(String authorizationHeader, Long templateId) {
        AuthSession session = resolveSession(authorizationHeader);
        requireAdmin(session);
        courseTemplateRepository.delete(loadCourseTemplate(templateId));
    }

    @Transactional
    public CourseResponse importCourseFromTemplate(
            String authorizationHeader,
            Long templateId,
            CreateCourseRequest request
    ) {
        AuthSession session = resolveSession(authorizationHeader);
        requireAdmin(session);

        CourseTemplate template = loadCourseTemplate(templateId);
        if (!template.isActive()) {
            throw new ForbiddenException("Course template is inactive");
        }

        CourseResponse course = createCourse(authorizationHeader, request);
        cloneDefinitionIntoCourse(template.getDefinitionJson(), course.id());
        return course;
    }

    @Transactional
    public CourseResponse duplicateCourse(
            String authorizationHeader,
            Long sourceCourseId,
            DuplicateCourseRequest request
    ) {
        AuthSession session = resolveSession(authorizationHeader);
        requireAdmin(session);

        loadCourse(sourceCourseId);
        CourseResponse course = createCourse(
                authorizationHeader,
                new CreateCourseRequest(
                        request.shortName(),
                        request.fullName(),
                        request.categoryId(),
                        request.summary(),
                        request.visible()
                )
        );
        cloneDefinitionIntoCourse(buildCourseTemplateDefinition(sourceCourseId), course.id());

        if (Boolean.TRUE.equals(request.copyTeachers())) {
            copyTeachers(sourceCourseId, course.id());
        }
        if (Boolean.TRUE.equals(request.copyStudents())) {
            copyStudents(sourceCourseId, course.id());
        }
        return course;
    }

    @Transactional
    public CourseResponse updateCourse(String authorizationHeader, Long courseId, UpdateCourseRequest request) {
        AuthSession session = resolveSession(authorizationHeader);
        requireCanEditCourse(session, courseId);

        if (request.shortName() == null
                && request.fullName() == null
                && request.categoryId() == null
                && request.summary() == null
                && request.visible() == null) {
            throw new IllegalArgumentException("At least one course field must be provided");
        }

        MoodleUpdateCourseRequest moodleRequest = new MoodleUpdateCourseRequest(
                courseId,
                trimToNull(request.shortName()),
                trimToNull(request.fullName()),
                request.categoryId(),
                trimToNull(request.summary()),
                request.visible()
        );

        return CourseResponse.from(moodleClient.updateCourse(moodleRequest));
    }

    @Transactional
    public void deleteCourse(String authorizationHeader, Long courseId) {
        AuthSession session = resolveSession(authorizationHeader);
        requireAdmin(session);
        courseTeacherAssignmentRepository.deleteByCourseId(courseId);
        courseStudentEnrollmentRepository.deleteByCourseId(courseId);
        List<Long> sectionIds = courseSectionRepository.findAllByCourseId(courseId)
                .stream()
                .map(CourseSection::getId)
                .toList();
        courseContentItemRepository.deleteBySectionIdIn(sectionIds);
        courseSectionRepository.deleteByCourseId(courseId);
        moodleClient.deleteCourse(courseId);
    }

    @Transactional
    public void assignTeacher(String authorizationHeader, Long courseId, Long userId) {
        AuthSession session = resolveSession(authorizationHeader);
        requireAdmin(session);

        loadCourse(courseId);
        UserAccount user = resolveUser(userId);
        if (user.getRole() != UserRole.ADMIN) {
            throw new IllegalArgumentException("User must have ADMIN role");
        }
        if (courseTeacherAssignmentRepository.existsByUserIdAndCourseId(userId, courseId)) {
            return;
        }

        CourseTeacherAssignment assignment = new CourseTeacherAssignment();
        assignment.setCourseId(courseId);
        assignment.setUser(user);
        courseTeacherAssignmentRepository.save(assignment);
    }

    @Transactional
    public void enrollStudent(String authorizationHeader, Long courseId, Long userId) {
        AuthSession session = resolveSession(authorizationHeader);
        requireCanEditCourse(session, courseId);

        loadCourse(courseId);
        UserAccount user = resolveUser(userId);
        if (user.getRole() != UserRole.STUDENT) {
            throw new IllegalArgumentException("User must have STUDENT role");
        }

        enrollStudentInCourse(courseId, user);
    }

    @Transactional
    public BulkStudentEnrollmentResponse bulkEnrollStudents(
            String authorizationHeader,
            Long courseId,
            MultipartFile file
    ) {
        AuthSession session = resolveSession(authorizationHeader);
        requireCanEditCourse(session, courseId);
        loadCourse(courseId);

        List<ExcelUsernameRow> usernameRows = readStudentUsernames(file);
        List<BulkStudentEnrollmentRowResponse> results = new ArrayList<>();
        int enrolledCount = 0;
        int alreadyEnrolledCount = 0;
        int failedCount = 0;

        for (ExcelUsernameRow usernameRow : usernameRows) {
            String username = usernameRow.username();
            try {
                UserAccount user = userAccountRepository.findByUsernameIgnoreCase(username)
                        .orElseThrow(() -> new NotFoundException("Student username was not found"));
                if (user.getRole() != UserRole.STUDENT) {
                    throw new IllegalArgumentException("User must have STUDENT role");
                }

                if (courseStudentEnrollmentRepository.existsByUserIdAndCourseId(user.getId(), courseId)) {
                    alreadyEnrolledCount++;
                    results.add(new BulkStudentEnrollmentRowResponse(
                            usernameRow.rowNumber(), username, "ALREADY_ENROLLED", "Student is already enrolled"
                    ));
                    continue;
                }

                enrollStudentInCourse(courseId, user);
                enrolledCount++;
                results.add(new BulkStudentEnrollmentRowResponse(
                        usernameRow.rowNumber(), username, "ENROLLED", "Student enrolled successfully"
                ));
            } catch (RuntimeException exception) {
                failedCount++;
                log.warn("Bulk course enrollment row failed: rowNumber={}, exceptionType={}",
                        usernameRow.rowNumber(), exception.getClass().getSimpleName());
                String message = trimToNull(exception.getMessage());
                results.add(new BulkStudentEnrollmentRowResponse(
                        usernameRow.rowNumber(),
                        username,
                        "FAILED",
                        message == null ? "Unable to enroll student" : message
                ));
            }
        }

        return new BulkStudentEnrollmentResponse(
                usernameRows.size(),
                enrolledCount,
                alreadyEnrolledCount,
                failedCount,
                results
        );
    }

    @Transactional
    public void unenrollStudent(String authorizationHeader, Long courseId, Long userId) {
        AuthSession session = resolveSession(authorizationHeader);
        requireCanEditCourse(session, courseId);

        loadCourse(courseId);
        UserAccount user = resolveUser(userId);
        if (user.getRole() != UserRole.STUDENT) {
            throw new IllegalArgumentException("User must have STUDENT role");
        }

        MoodleUserMapping mapping = moodleUserMappingRepository.findByUser(user)
                .orElseThrow(() -> new NotFoundException("User has no Moodle mapping"));
        moodleClient.unenrollStudent(courseId, mapping.getMoodleUserId());
        courseStudentEnrollmentRepository.deleteByUserIdAndCourseId(userId, courseId);
    }

    @Transactional
    public void unassignTeacher(String authorizationHeader, Long courseId, Long userId) {
        AuthSession session = resolveSession(authorizationHeader);
        requireAdmin(session);
        courseTeacherAssignmentRepository.deleteByUserIdAndCourseId(userId, courseId);
    }

    @Transactional
    public CourseSectionResponse createSection(String authorizationHeader, Long courseId, CreateCourseSectionRequest request) {
        AuthSession session = resolveSession(authorizationHeader);
        requireCanEditCourse(session, courseId);
        loadCourse(courseId);

        CourseSection section = new CourseSection();
        section.setCourseId(courseId);
        section.setTitle(requireText(request.title(), "Section title is required"));
        section.setDescription(trimToNull(request.description()));
        section.setVisible(request.visible() == null || request.visible());
        section.setLocked(request.locked() != null && request.locked());
        section.setOrderIndex(resolveSectionOrderIndex(courseId, request.orderIndex()));
        section = courseSectionRepository.save(section);
        normalizeSectionOrdering(courseId, section.getId(), section.getOrderIndex());
        return CourseSectionResponse.from(loadSection(courseId, section.getId()), List.of());
    }

    @Transactional
    public CourseSectionResponse updateSection(
            String authorizationHeader,
            Long courseId,
            Long sectionId,
            UpdateCourseSectionRequest request
    ) {
        AuthSession session = resolveSession(authorizationHeader);
        requireCanEditCourse(session, courseId);
        CourseSection section = loadSection(courseId, sectionId);

        if (request.title() != null) {
            section.setTitle(requireText(request.title(), "Section title is required"));
        }
        if (request.description() != null) {
            section.setDescription(trimToNull(request.description()));
        }
        if (request.orderIndex() != null) {
            section.setOrderIndex(request.orderIndex());
        }
        if (request.visible() != null) {
            section.setVisible(request.visible());
        }
        if (request.locked() != null) {
            section.setLocked(request.locked());
        }
        if (request.orderIndex() != null) {
            section.setOrderIndex(request.orderIndex());
            section = courseSectionRepository.save(section);
            normalizeSectionOrdering(courseId, section.getId(), section.getOrderIndex());
        } else {
            section = courseSectionRepository.save(section);
        }
        return CourseSectionResponse.from(loadSection(courseId, section.getId()), loadSectionItems(section.getId()));
    }

    @Transactional
    public CourseSectionResponse reorderSection(
            String authorizationHeader,
            Long courseId,
            Long sectionId,
            ReorderCourseSectionRequest request
    ) {
        AuthSession session = resolveSession(authorizationHeader);
        requireCanEditCourse(session, courseId);
        loadSection(courseId, sectionId);
        normalizeSectionOrdering(courseId, sectionId, request.orderIndex());
        return CourseSectionResponse.from(loadSection(courseId, sectionId), loadSectionItems(sectionId));
    }

    @Transactional
    public void deleteSection(String authorizationHeader, Long courseId, Long sectionId) {
        AuthSession session = resolveSession(authorizationHeader);
        requireCanEditCourse(session, courseId);
        CourseSection section = loadSection(courseId, sectionId);
        courseContentProgressRepository.deleteBySectionId(section.getId());
        courseContentItemRepository.deleteBySectionId(section.getId());
        courseSectionRepository.delete(section);
    }

    @Transactional
    public CourseContentItemResponse createContentItem(
            String authorizationHeader,
            Long courseId,
            Long sectionId,
            CreateCourseContentItemRequest request
    ) {
        AuthSession session = resolveSession(authorizationHeader);
        requireCanEditCourse(session, courseId);
        CourseSection section = loadSection(courseId, sectionId);

        CourseContentItem item = new CourseContentItem();
        item.setSection(section);
        item.setType(request.type());
        item.setTitle(requireText(request.title(), "Content item title is required"));
        item.setDescription(trimToNull(request.description()));
        item.setOrderIndex(resolveContentOrderIndex(sectionId, request.orderIndex()));
        item.setRequired(request.required() == null || request.required());
        item.setVisible(request.visible() == null || request.visible());
        item.setDurationSeconds(request.durationSeconds());
        item.setSourceUrl(trimToNull(request.sourceUrl()));
        item.setPayloadJson(trimToNull(request.payloadJson()));
        if (request.published() != null && request.published()) {
            item.setPublishedAt(Instant.now());
        }
        validateContentItem(item.getType(), item.getSourceUrl(), item.getPayloadJson());
        item = courseContentItemRepository.save(item);
        normalizeContentOrdering(sectionId, item.getId(), item.getOrderIndex());
        return CourseContentItemResponse.from(loadItem(sectionId, item.getId()));
    }

    @Transactional
    public CourseContentItemResponse updateContentItem(
            String authorizationHeader,
            Long courseId,
            Long sectionId,
            Long itemId,
            UpdateCourseContentItemRequest request
    ) {
        AuthSession session = resolveSession(authorizationHeader);
        requireCanEditCourse(session, courseId);
        loadSection(courseId, sectionId);
        CourseContentItem item = loadItem(sectionId, itemId);

        if (request.type() != null) {
            item.setType(request.type());
        }
        if (request.title() != null) {
            item.setTitle(requireText(request.title(), "Content item title is required"));
        }
        if (request.description() != null) {
            item.setDescription(trimToNull(request.description()));
        }
        if (request.orderIndex() != null) {
            item.setOrderIndex(request.orderIndex());
        }
        if (request.required() != null) {
            item.setRequired(request.required());
        }
        if (request.visible() != null) {
            item.setVisible(request.visible());
        }
        if (request.durationSeconds() != null) {
            item.setDurationSeconds(request.durationSeconds());
        }
        if (request.sourceUrl() != null) {
            item.setSourceUrl(trimToNull(request.sourceUrl()));
        }
        if (request.payloadJson() != null) {
            item.setPayloadJson(trimToNull(request.payloadJson()));
        }
        if (request.published() != null) {
            item.setPublishedAt(request.published() ? Instant.now() : null);
        }
        validateContentItem(
                item.getType(),
                item.getSourceUrl(),
                item.getPayloadJson()
        );
        if (request.orderIndex() != null) {
            item = courseContentItemRepository.save(item);
            normalizeContentOrdering(sectionId, item.getId(), item.getOrderIndex());
        } else {
            item = courseContentItemRepository.save(item);
        }
        return CourseContentItemResponse.from(loadItem(sectionId, item.getId()));
    }

    @Transactional
    public CourseContentItemResponse reorderContentItem(
            String authorizationHeader,
            Long courseId,
            Long sectionId,
            Long itemId,
            ReorderCourseContentItemRequest request
    ) {
        AuthSession session = resolveSession(authorizationHeader);
        requireCanEditCourse(session, courseId);
        loadSection(courseId, sectionId);
        loadItem(sectionId, itemId);
        normalizeContentOrdering(sectionId, itemId, request.orderIndex());
        return CourseContentItemResponse.from(loadItem(sectionId, itemId));
    }

    @Transactional
    public void deleteContentItem(String authorizationHeader, Long courseId, Long sectionId, Long itemId) {
        AuthSession session = resolveSession(authorizationHeader);
        requireCanEditCourse(session, courseId);
        loadSection(courseId, sectionId);
        CourseContentItem item = loadItem(sectionId, itemId);
        courseContentProgressRepository.deleteByContentItemId(item.getId());
        courseContentItemRepository.delete(item);
    }

    @Transactional
    public CourseContentFileResponse uploadContentPdf(
            String authorizationHeader, Long courseId, MultipartFile file
    ) {
        return uploadContentFile(authorizationHeader, courseId, file, "PDF");
    }

    @Transactional
    public CourseContentFileResponse uploadContentVideo(
            String authorizationHeader, Long courseId, MultipartFile file
    ) {
        return uploadContentFile(authorizationHeader, courseId, file, "VIDEO");
    }

    private CourseContentFileResponse uploadContentFile(
            String authorizationHeader, Long courseId, MultipartFile file, String fileKind
    ) {
        AuthSession session = resolveSession(authorizationHeader);
        requireCanEditCourse(session, courseId);
        loadCourse(courseId);
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException(fileKind + " file is required");
        }
        int maxUploadSizeMb = systemSettingsService.getMaxUploadSizeMb();
        if (file.getSize() > maxUploadSizeMb * 1024L * 1024L) {
            throw new IllegalArgumentException(fileKind + " file must not exceed " + maxUploadSizeMb + " MB");
        }
        String originalName = file.getOriginalFilename();
        String extension = resolveUploadExtension(originalName, fileKind);
        if (extension == null) {
            throw new IllegalArgumentException("PDF".equals(fileKind)
                    ? "Only PDF files can be uploaded"
                    : "Only MP4, MOV, WebM, or OGV video files can be uploaded");
        }

        String fileName = java.util.UUID.randomUUID() + extension;
        try {
            Path storageDirectory = Path.of(contentStoragePath).toAbsolutePath().normalize();
            Files.createDirectories(storageDirectory);
            Path target = storageDirectory.resolve(fileName).normalize();
            if (!target.startsWith(storageDirectory)) {
                throw new IllegalArgumentException("Invalid upload path");
            }
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException exception) {
            throw new IntegrationException("Failed to store " + fileKind + " file");
        }

        return new CourseContentFileResponse(
                originalName,
                publicBaseUrl.replaceAll("/$", "") + "/content-files/" + fileName
        );
    }

    private String resolveUploadExtension(String originalName, String fileKind) {
        if (originalName == null) {
            return null;
        }
        String lowerName = originalName.toLowerCase();
        if ("PDF".equals(fileKind)) {
            return lowerName.endsWith(".pdf") ? ".pdf" : null;
        }
        if (lowerName.endsWith(".mp4")) return ".mp4";
        if (lowerName.endsWith(".mov")) return ".mov";
        if (lowerName.endsWith(".webm")) return ".webm";
        if (lowerName.endsWith(".ogv")) return ".ogv";
        return null;
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

    private String extractBearerToken(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            throw new UnauthorizedException("Missing bearer token");
        }
        return authorizationHeader.substring(7).trim();
    }

    private List<CourseResponse> getAccessibleCourses(AuthSession session) {
        Map<Long, CourseResponse> courses = new LinkedHashMap<>();
        MoodleUserMapping mapping = moodleUserMappingRepository.findByUser(session.getUser())
                .orElseThrow(() -> new UnauthorizedException("User has no Moodle mapping"));

        moodleClient.getUserCourses(mapping.getMoodleUserId())
                .forEach(course -> courses.put(course.id(), CourseResponse.from(course)));

        return courses.values().stream()
                .map(course -> courseEnrollmentProgressService
                        .refreshCompletion(session.getUser(), course.id())
                        .map(course::withEnrollment)
                        .orElse(course))
                .toList();
    }

    private CourseDetailsResponse buildCourseDetails(Long courseId) {
        var course = loadCourse(courseId);
        List<CourseTeacherResponse> teachers = courseTeacherAssignmentRepository.findAllByCourseId(courseId)
                .stream()
                .map(CourseTeacherResponse::from)
                .toList();
        List<CourseSectionResponse> sections = loadCourseSections(courseId);
        long studentCount = courseStudentEnrollmentRepository.findAllByCourseId(courseId).size();
        long itemCount = sections.stream().mapToLong(section -> section.items().size()).sum();
        return new CourseDetailsResponse(
                course.id(),
                course.shortName(),
                course.fullName(),
                course.displayName(),
                course.categoryId(),
                course.summary(),
                teachers,
                Math.toIntExact(studentCount),
                sections.size(),
                Math.toIntExact(itemCount),
                sections
        );
    }

    private List<CourseSectionResponse> loadCourseSections(Long courseId) {
        return courseSectionRepository.findAllByCourseIdOrderByOrderIndexAscIdAsc(courseId)
                .stream()
                .map(section -> CourseSectionResponse.from(section, loadSectionItems(section.getId())))
                .toList();
    }

    private List<CourseContentItemResponse> loadSectionItems(Long sectionId) {
        return courseContentItemRepository.findAllBySectionIdOrderByOrderIndexAscIdAsc(sectionId)
                .stream()
                .map(CourseContentItemResponse::from)
                .toList();
    }

    private CourseSection loadSection(Long courseId, Long sectionId) {
        return courseSectionRepository.findByIdAndCourseId(sectionId, courseId)
                .orElseThrow(() -> new NotFoundException("Section not found"));
    }

    private CourseContentItem loadItem(Long sectionId, Long itemId) {
        return courseContentItemRepository.findByIdAndSectionId(itemId, sectionId)
                .orElseThrow(() -> new NotFoundException("Content item not found"));
    }

    private void normalizeSectionOrdering(Long courseId, Long movedSectionId, Integer targetOrderIndex) {
        List<CourseSection> sections = new ArrayList<>(courseSectionRepository.findAllByCourseIdOrderByOrderIndexAscIdAsc(courseId));
        if (sections.isEmpty()) {
            return;
        }
        CourseSection moved = sections.stream()
                .filter(section -> section.getId().equals(movedSectionId))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Section not found"));
        sections.remove(moved);
        int targetIndex = clampOrderIndex(targetOrderIndex, sections.size() + 1) - 1;
        sections.add(targetIndex, moved);
        for (int index = 0; index < sections.size(); index++) {
            sections.get(index).setOrderIndex(index + 1);
        }
        courseSectionRepository.saveAll(sections);
    }

    private void normalizeContentOrdering(Long sectionId, Long movedItemId, Integer targetOrderIndex) {
        List<CourseContentItem> items = new ArrayList<>(courseContentItemRepository.findAllBySectionIdOrderByOrderIndexAscIdAsc(sectionId));
        if (items.isEmpty()) {
            return;
        }
        CourseContentItem moved = items.stream()
                .filter(item -> item.getId().equals(movedItemId))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Content item not found"));
        items.remove(moved);
        int targetIndex = clampOrderIndex(targetOrderIndex, items.size() + 1) - 1;
        items.add(targetIndex, moved);
        for (int index = 0; index < items.size(); index++) {
            items.get(index).setOrderIndex(index + 1);
        }
        courseContentItemRepository.saveAll(items);
    }

    private int clampOrderIndex(Integer requestedOrderIndex, int maxAllowed) {
        if (requestedOrderIndex == null) {
            return maxAllowed;
        }
        return Math.max(1, Math.min(requestedOrderIndex, maxAllowed));
    }

    private void validateContentItem(CourseContentType type, String sourceUrl, String payloadJson) {
        if (type == null) {
            throw new IllegalArgumentException("Content item type is required");
        }
        if ((type == CourseContentType.VIDEO || type == CourseContentType.PDF)
                && trimToNull(sourceUrl) == null) {
            throw new IllegalArgumentException(type.name() + " content requires sourceUrl");
        }
        if (type != CourseContentType.QUIZ && payloadJson != null) {
            parseJson(payloadJson, "payloadJson must be valid JSON");
        }
    }

    private JsonNode parseJson(String value, String errorMessage) {
        try {
            return objectMapper.readTree(value);
        } catch (Exception exception) {
            throw new IllegalArgumentException(errorMessage);
        }
    }

    private CourseTemplate loadCourseTemplate(Long templateId) {
        return courseTemplateRepository.findById(templateId)
                .orElseThrow(() -> new NotFoundException("Course template not found"));
    }

    private String buildCourseTemplateDefinition(Long sourceCourseId) {
        var course = loadCourse(sourceCourseId);
        Map<String, Object> root = new LinkedHashMap<>();
        Map<String, Object> courseNode = new LinkedHashMap<>();
        courseNode.put("id", course.id());
        courseNode.put("shortName", course.shortName());
        courseNode.put("fullName", course.fullName());
        courseNode.put("displayName", course.displayName());
        courseNode.put("categoryId", course.categoryId());
        courseNode.put("summary", course.summary());
        root.put("sourceCourseId", sourceCourseId);
        root.put("course", courseNode);
        root.put("sections", courseSectionRepository.findAllByCourseIdOrderByOrderIndexAscIdAsc(sourceCourseId)
                .stream()
                .map(section -> buildSectionSnapshot(section))
                .toList());
        return writeJson(root);
    }

    private Map<String, Object> buildSectionSnapshot(CourseSection section) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("title", section.getTitle());
        snapshot.put("description", section.getDescription());
        snapshot.put("orderIndex", section.getOrderIndex());
        snapshot.put("visible", section.isVisible());
        snapshot.put("locked", section.isLocked());
        snapshot.put("items", courseContentItemRepository.findAllBySectionIdOrderByOrderIndexAscIdAsc(section.getId())
                .stream()
                .map(this::buildItemSnapshot)
                .toList());
        return snapshot;
    }

    private Map<String, Object> buildItemSnapshot(CourseContentItem item) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("type", item.getType().name());
        snapshot.put("title", item.getTitle());
        snapshot.put("description", item.getDescription());
        snapshot.put("orderIndex", item.getOrderIndex());
        snapshot.put("required", item.isRequired());
        snapshot.put("visible", item.isVisible());
        snapshot.put("durationSeconds", item.getDurationSeconds());
        snapshot.put("sourceUrl", item.getSourceUrl());
        snapshot.put("payloadJson", item.getPayloadJson());
        snapshot.put("publishedAt", item.getPublishedAt() == null ? null : item.getPublishedAt().toString());
        return snapshot;
    }

    private void cloneDefinitionIntoCourse(String definitionJson, Long targetCourseId) {
        JsonNode root = parseJson(definitionJson, "Invalid course template definition");
        JsonNode sections = root.path("sections");
        if (!sections.isArray()) {
            throw new IllegalArgumentException("Template definition must contain sections");
        }

        for (JsonNode sectionNode : sections) {
            CourseSection section = new CourseSection();
            section.setCourseId(targetCourseId);
            section.setTitle(requireText(sectionNode.path("title").asText(null), "Section title is required"));
            section.setDescription(trimToNull(sectionNode.path("description").asText(null)));
            section.setOrderIndex(sectionNode.path("orderIndex").isMissingNode() ? 1 : sectionNode.path("orderIndex").asInt(1));
            section.setVisible(!sectionNode.has("visible") || sectionNode.path("visible").asBoolean(true));
            section.setLocked(sectionNode.path("locked").asBoolean(false));
            section = courseSectionRepository.save(section);

            JsonNode items = sectionNode.path("items");
            if (!items.isArray()) {
                continue;
            }
            for (JsonNode itemNode : items) {
                CourseContentItem item = new CourseContentItem();
                item.setSection(section);
                item.setType(parseContentType(itemNode.path("type").asText(null)));
                item.setTitle(requireText(itemNode.path("title").asText(null), "Content item title is required"));
                item.setDescription(trimToNull(itemNode.path("description").asText(null)));
                item.setOrderIndex(itemNode.path("orderIndex").isMissingNode() ? 1 : itemNode.path("orderIndex").asInt(1));
                item.setRequired(!itemNode.has("required") || itemNode.path("required").asBoolean(true));
                item.setVisible(!itemNode.has("visible") || itemNode.path("visible").asBoolean(true));
                item.setDurationSeconds(itemNode.path("durationSeconds").isMissingNode() || itemNode.path("durationSeconds").isNull()
                        ? null
                        : itemNode.path("durationSeconds").asInt());
                item.setSourceUrl(trimToNull(itemNode.path("sourceUrl").asText(null)));
                item.setPayloadJson(trimToNull(itemNode.path("payloadJson").asText(null)));
                if (itemNode.hasNonNull("publishedAt")) {
                    item.setPublishedAt(Instant.parse(itemNode.path("publishedAt").asText()));
                }
                validateContentItem(item.getType(), item.getSourceUrl(), item.getPayloadJson());
                courseContentItemRepository.save(item);
            }
        }
    }

    private CourseTemplateType parseTemplateType(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Template type is required");
        }
        try {
            return CourseTemplateType.valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Invalid template type");
        }
    }

    private CourseContentType parseContentType(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Content item type is required");
        }
        try {
            return CourseContentType.valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Invalid content item type");
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to serialize template definition", exception);
        }
    }

    private void copyTeachers(Long sourceCourseId, Long targetCourseId) {
        courseTeacherAssignmentRepository.findAllByCourseId(sourceCourseId)
                .forEach(assignment -> {
                    if (courseTeacherAssignmentRepository.existsByUserIdAndCourseId(assignment.getUser().getId(), targetCourseId)) {
                        return;
                    }
                    CourseTeacherAssignment copy = new CourseTeacherAssignment();
                    copy.setCourseId(targetCourseId);
                    copy.setUser(assignment.getUser());
                    courseTeacherAssignmentRepository.save(copy);
                });
    }

    private void copyStudents(Long sourceCourseId, Long targetCourseId) {
        courseStudentEnrollmentRepository.findAllByCourseId(sourceCourseId)
                .forEach(enrollment -> {
                    UserAccount user = enrollment.getUser();
                    if (user.getRole() != UserRole.STUDENT) {
                        return;
                    }
                    if (courseStudentEnrollmentRepository.existsByUserIdAndCourseId(user.getId(), targetCourseId)) {
                        return;
                    }
                    MoodleUserMapping mapping = moodleUserMappingRepository.findByUser(user)
                            .orElseThrow(() -> new NotFoundException("User has no Moodle mapping"));
                    moodleClient.enrollStudent(targetCourseId, mapping.getMoodleUserId());
                    CourseStudentEnrollment copy = new CourseStudentEnrollment();
                    copy.setCourseId(targetCourseId);
                    copy.setUser(user);
                    courseStudentEnrollmentRepository.save(copy);
                });
    }

    private Integer resolveSectionOrderIndex(Long courseId, Integer requestedOrderIndex) {
        if (requestedOrderIndex != null) {
            return requestedOrderIndex;
        }
        return courseSectionRepository.findFirstByCourseIdOrderByOrderIndexDescIdDesc(courseId)
                .map(section -> section.getOrderIndex() + 1)
                .orElse(1);
    }

    private Integer resolveContentOrderIndex(Long sectionId, Integer requestedOrderIndex) {
        if (requestedOrderIndex != null) {
            return requestedOrderIndex;
        }
        return courseContentItemRepository.findFirstBySectionIdOrderByOrderIndexDescIdDesc(sectionId)
                .map(item -> item.getOrderIndex() + 1)
                .orElse(1);
    }

    private String requireText(String value, String message) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            throw new IllegalArgumentException(message);
        }
        return trimmed;
    }

    private void requireAdmin(AuthSession session) {
        if (session.getUser().getRole() != UserRole.ADMIN) {
            throw new ForbiddenException("Admin access is required");
        }
    }

    private void requireCanAccessCourse(AuthSession session, Long courseId) {
        if (session.getUser().getRole() == UserRole.ADMIN) {
            return;
        }
        if (isInEnrolledCourses(session, courseId)) {
            return;
        }
        throw new NotFoundException("Course not found for current user");
    }

    private void requireCanEditCourse(AuthSession session, Long courseId) {
        requireAdmin(session);
    }

    private boolean isInEnrolledCourses(AuthSession session, Long courseId) {
        MoodleUserMapping mapping = moodleUserMappingRepository.findByUser(session.getUser())
                .orElseThrow(() -> new UnauthorizedException("User has no Moodle mapping"));
        return moodleClient.getUserCourses(mapping.getMoodleUserId())
                .stream()
                .anyMatch(course -> course.id().equals(courseId));
    }

    private com.carbonedge.backend.dto.moodle.MoodleCourseRecord loadCourse(Long courseId) {
        try {
            return moodleClient.getCourseById(courseId);
        } catch (IntegrationException exception) {
            String message = exception.getMessage() == null ? "" : exception.getMessage().toLowerCase();
            if (message.contains("not found")) {
                throw new NotFoundException("Course not found");
            }
            throw exception;
        }
    }

    private UserAccount resolveUser(Long userId) {
        return userAccountRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));
    }

    private boolean enrollStudentInCourse(Long courseId, UserAccount user) {
        if (courseStudentEnrollmentRepository.existsByUserIdAndCourseId(user.getId(), courseId)) {
            return false;
        }
        MoodleUserMapping mapping = moodleUserMappingRepository.findByUser(user)
                .orElseThrow(() -> new NotFoundException("User has no Moodle mapping"));
        moodleClient.enrollStudent(courseId, mapping.getMoodleUserId());
        CourseStudentEnrollment enrollment = new CourseStudentEnrollment();
        enrollment.setCourseId(courseId);
        enrollment.setUser(user);
        courseStudentEnrollmentRepository.save(enrollment);
        return true;
    }

    private List<ExcelUsernameRow> readStudentUsernames(MultipartFile file) {
        validateStudentImportFile(file);
        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            if (workbook.getNumberOfSheets() == 0) {
                throw new IllegalArgumentException("Excel file does not contain a worksheet");
            }
            Sheet sheet = workbook.getSheetAt(0);
            DataFormatter formatter = new DataFormatter(Locale.ROOT);
            HeaderPosition header = findUsernameHeader(sheet, formatter);
            List<ExcelUsernameRow> rows = new ArrayList<>();

            for (int rowIndex = header.rowIndex() + 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row == null) {
                    continue;
                }
                String username = trimToNull(formatter.formatCellValue(row.getCell(header.columnIndex())));
                if (username == null) {
                    continue;
                }
                rows.add(new ExcelUsernameRow(rowIndex + 1, username));
                if (rows.size() > MAX_STUDENT_IMPORT_ROWS) {
                    throw new IllegalArgumentException(
                            "Excel file contains more than " + MAX_STUDENT_IMPORT_ROWS + " usernames"
                    );
                }
            }

            if (rows.isEmpty()) {
                throw new IllegalArgumentException("Excel file does not contain any student usernames");
            }
            return rows;
        } catch (EncryptedDocumentException exception) {
            throw new IllegalArgumentException("Password-protected Excel files are not supported");
        } catch (IOException exception) {
            throw new IllegalArgumentException("Unable to read Excel file");
        }
    }

    private void validateStudentImportFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Excel file is required");
        }
        if (file.getSize() > MAX_STUDENT_IMPORT_FILE_SIZE) {
            throw new IllegalArgumentException("Excel file must not exceed 5 MB");
        }
        String fileName = file.getOriginalFilename();
        String normalizedFileName = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        if (!normalizedFileName.endsWith(".xlsx") && !normalizedFileName.endsWith(".xls")) {
            throw new IllegalArgumentException("Only .xlsx and .xls files are supported");
        }
    }

    private HeaderPosition findUsernameHeader(Sheet sheet, DataFormatter formatter) {
        int lastHeaderCandidate = Math.min(sheet.getLastRowNum(), 19);
        for (int rowIndex = sheet.getFirstRowNum(); rowIndex <= lastHeaderCandidate; rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row == null) {
                continue;
            }
            for (int columnIndex = row.getFirstCellNum(); columnIndex >= 0 && columnIndex < row.getLastCellNum(); columnIndex++) {
                String value = trimToNull(formatter.formatCellValue(row.getCell(columnIndex)));
                if (value != null && "username".equalsIgnoreCase(value)) {
                    return new HeaderPosition(rowIndex, columnIndex);
                }
            }
        }
        throw new IllegalArgumentException("Excel file must contain a 'username' column");
    }

    private record ExcelUsernameRow(int rowNumber, String username) {
    }

    private record HeaderPosition(int rowIndex, int columnIndex) {
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private boolean matchesCourseKeyword(CourseResponse course, String keyword) {
        if (keyword == null) {
            return true;
        }

        String haystack = String.join(" ",
                normalizeText(course.shortName()),
                normalizeText(course.fullName()),
                normalizeText(course.displayName()),
                normalizeText(course.summary()),
                course.categoryId() == null ? "" : String.valueOf(course.categoryId()));
        return haystack.toLowerCase().contains(keyword.toLowerCase());
    }

    private String normalizeText(String value) {
        return value == null ? "" : value.trim();
    }

    private int compareNullable(String left, String right) {
        String leftValue = left == null ? "" : left;
        String rightValue = right == null ? "" : right;
        return leftValue.compareToIgnoreCase(rightValue);
    }
}
