package com.carbonedge.backend.integration.moodle;

import com.carbonedge.backend.client.moodle.MoodleClient;
import com.carbonedge.backend.config.MoodleIntegrationProperties;
import com.carbonedge.backend.dto.moodle.MoodleCategoryRecord;
import com.carbonedge.backend.dto.moodle.MoodleCreateCategoryRequest;
import com.carbonedge.backend.dto.moodle.MoodleCreateCourseRequest;
import com.carbonedge.backend.dto.moodle.MoodleCreateUserRequest;
import com.carbonedge.backend.dto.moodle.MoodleCourseRecord;
import com.carbonedge.backend.dto.moodle.MoodleUpdateCategoryRequest;
import com.carbonedge.backend.dto.moodle.MoodleUpdateCourseRequest;
import com.carbonedge.backend.dto.moodle.MoodleUpdateUserRequest;
import com.carbonedge.backend.dto.moodle.MoodleUserRecord;
import com.carbonedge.backend.exception.IntegrationException;
import com.carbonedge.backend.exception.NotFoundException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Base64;
import java.util.Map;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "moodle.integration", name = "mode", havingValue = "mock")
public class MockMoodleClient implements MoodleClient {

    private final AtomicLong moodleUserSequence = new AtomicLong(1000);
    private final AtomicLong moodleCourseSequence = new AtomicLong(5000);
    private final AtomicLong moodleCategorySequence = new AtomicLong(100);
    private final Map<String, MoodleUserRecord> usersByUsername = new ConcurrentHashMap<>();
    private final Map<String, String> passwordsByUsername = new ConcurrentHashMap<>();
    private final Map<Long, MoodleCourseRecord> coursesById = new ConcurrentHashMap<>();
    private final Map<Long, MoodleCategoryRecord> categoriesById = new ConcurrentHashMap<>();
    private final Map<Long, Set<Long>> enrolledCoursesByMoodleUserId = new ConcurrentHashMap<>();
    private final MoodleIntegrationProperties properties;

    public MockMoodleClient(MoodleIntegrationProperties properties) {
        this.properties = properties;
        MoodleCategoryRecord rootCategory = new MoodleCategoryRecord(
                1L,
                "Miscellaneous",
                null,
                "",
                0L,
                1,
                0,
                true,
                true,
                0L,
                1,
                "/1",
                null
        );
        categoriesById.put(rootCategory.id(), rootCategory);
    }

    @Override
    public MoodleUserRecord createUser(MoodleCreateUserRequest request) {
        if (!"mock".equalsIgnoreCase(properties.getMode())) {
            throw new IntegrationException("Mock Moodle client is disabled");
        }
        MoodleUserRecord existing = usersByUsername.get(request.username().toLowerCase());
        if (existing != null) {
            throw new IntegrationException("Moodle username already exists");
        }
        MoodleUserRecord created = new MoodleUserRecord(
                moodleUserSequence.incrementAndGet(),
                request.username(),
                request.email()
        );
        usersByUsername.put(request.username().toLowerCase(), created);
        passwordsByUsername.put(request.username().toLowerCase(), request.password());
        return created;
    }

    @Override
    public void updateUser(MoodleUpdateUserRequest request) {
        if (!"mock".equalsIgnoreCase(properties.getMode())) {
            throw new IntegrationException("Mock Moodle client is disabled");
        }
        MoodleUserRecord existing = usersByUsername.values()
                .stream()
                .filter(user -> user.id().equals(request.id()))
                .findFirst()
                .orElseThrow(() -> new IntegrationException("Moodle user not found"));
        usersByUsername.put(
                existing.username().toLowerCase(),
                new MoodleUserRecord(existing.id(), existing.username(), request.email())
        );
    }

    @Override
    public MoodleCourseRecord createCourse(MoodleCreateCourseRequest request) {
        if (!"mock".equalsIgnoreCase(properties.getMode())) {
            throw new IntegrationException("Mock Moodle client is disabled");
        }
        Long id = moodleCourseSequence.incrementAndGet();
        MoodleCourseRecord created = new MoodleCourseRecord(
                id,
                request.shortName(),
                request.fullName(),
                request.fullName(),
                request.categoryId(),
                request.summary() == null ? "" : request.summary()
        );
        coursesById.put(id, created);
        return created;
    }

    @Override
    public MoodleCategoryRecord createCategory(MoodleCreateCategoryRequest request) {
        if (!"mock".equalsIgnoreCase(properties.getMode())) {
            throw new IntegrationException("Mock Moodle client is disabled");
        }
        if (request.parent() != 0L && !categoriesById.containsKey(request.parent())) {
            throw new NotFoundException("Parent category not found");
        }
        Long id = moodleCategorySequence.incrementAndGet();
        MoodleCategoryRecord parent = categoriesById.getOrDefault(request.parent(), categoriesById.get(1L));
        MoodleCategoryRecord created = new MoodleCategoryRecord(
                id,
                request.name(),
                request.idNumber(),
                request.description() == null ? "" : request.description(),
                request.parent(),
                categoriesById.size() + 1,
                0,
                request.visible(),
                request.visible(),
                0L,
                parent == null ? 1 : parent.depth() + 1,
                (parent == null ? "" : parent.path()) + "/" + id,
                request.theme()
        );
        categoriesById.put(id, created);
        return created;
    }

    @Override
    public void enrollStudent(Long courseId, Long moodleUserId) {
        if (!"mock".equalsIgnoreCase(properties.getMode())) {
            throw new IntegrationException("Mock Moodle client is disabled");
        }
        if (!usersByUsername.values().stream().anyMatch(user -> user.id().equals(moodleUserId))) {
            throw new IntegrationException("Moodle user not found");
        }
        if (courseId != 501L && !coursesById.containsKey(courseId)) {
            throw new IntegrationException("Moodle course not found");
        }
        enrolledCoursesByMoodleUserId
                .computeIfAbsent(moodleUserId, ignored -> ConcurrentHashMap.newKeySet())
                .add(courseId);
    }

    @Override
    public void unenrollStudent(Long courseId, Long moodleUserId) {
        if (!"mock".equalsIgnoreCase(properties.getMode())) {
            throw new IntegrationException("Mock Moodle client is disabled");
        }
        Set<Long> enrolledCourses = enrolledCoursesByMoodleUserId.get(moodleUserId);
        if (enrolledCourses != null) {
            enrolledCourses.remove(courseId);
        }
    }

    @Override
    public MoodleCourseRecord updateCourse(MoodleUpdateCourseRequest request) {
        if (!"mock".equalsIgnoreCase(properties.getMode())) {
            throw new IntegrationException("Mock Moodle client is disabled");
        }
        MoodleCourseRecord existing = getCourseById(request.id());
        MoodleCourseRecord updated = new MoodleCourseRecord(
                existing.id(),
                request.shortName() == null ? existing.shortName() : request.shortName(),
                request.fullName() == null ? existing.fullName() : request.fullName(),
                request.fullName() == null ? existing.displayName() : request.fullName(),
                request.categoryId() == null ? existing.categoryId() : request.categoryId(),
                request.summary() == null ? existing.summary() : request.summary()
        );
        coursesById.put(updated.id(), updated);
        return updated;
    }

    @Override
    public MoodleCategoryRecord updateCategory(MoodleUpdateCategoryRequest request) {
        if (!"mock".equalsIgnoreCase(properties.getMode())) {
            throw new IntegrationException("Mock Moodle client is disabled");
        }
        MoodleCategoryRecord existing = getCategoryById(request.id());
        Long parent = request.parent() == null ? existing.parent() : request.parent().longValue();
        if (parent != 0L && !categoriesById.containsKey(parent)) {
            throw new NotFoundException("Parent category not found");
        }
        MoodleCategoryRecord parentCategory = categoriesById.get(parent);
        MoodleCategoryRecord updated = new MoodleCategoryRecord(
                existing.id(),
                request.name() == null ? existing.name() : request.name(),
                request.idNumber() == null ? existing.idNumber() : request.idNumber(),
                request.description() == null ? existing.description() : request.description(),
                parent,
                existing.sortOrder(),
                existing.courseCount(),
                request.visible() == null ? existing.visible() : request.visible(),
                request.visible() == null ? existing.visibleOld() : request.visible(),
                existing.timeModified(),
                parentCategory == null ? existing.depth() : parentCategory.depth() + 1,
                parentCategory == null ? existing.path() : parentCategory.path() + "/" + existing.id(),
                request.theme() == null ? existing.theme() : request.theme()
        );
        categoriesById.put(updated.id(), updated);
        return updated;
    }

    @Override
    public void deleteCourse(Long courseId) {
        if (!"mock".equalsIgnoreCase(properties.getMode())) {
            throw new IntegrationException("Mock Moodle client is disabled");
        }
        if (coursesById.remove(courseId) == null && courseId != 501L) {
            throw new IntegrationException("Moodle course not found");
        }
    }

    @Override
    public void deleteCategory(Long categoryId) {
        if (!"mock".equalsIgnoreCase(properties.getMode())) {
            throw new IntegrationException("Mock Moodle client is disabled");
        }
        if (categoryId == 1L) {
            throw new IntegrationException("Root category cannot be deleted");
        }
        if (categoriesById.remove(categoryId) == null) {
            throw new NotFoundException("Category not found");
        }
    }

    @Override
    public MoodleCourseRecord getCourseById(Long courseId) {
        if (!"mock".equalsIgnoreCase(properties.getMode())) {
            throw new IntegrationException("Mock Moodle client is disabled");
        }
        if (courseId == 501L) {
            return new MoodleCourseRecord(
                    501L,
                    "COURSE-501",
                    "Sample Course 501",
                    "Sample Course 501",
                    1,
                    "Mock course for launch flow tests"
            );
        }
        MoodleCourseRecord record = coursesById.get(courseId);
        if (record == null) {
            throw new IntegrationException("Moodle course not found");
        }
        return record;
    }

    @Override
    public MoodleCategoryRecord getCategoryById(Long categoryId) {
        if (!"mock".equalsIgnoreCase(properties.getMode())) {
            throw new IntegrationException("Mock Moodle client is disabled");
        }
        MoodleCategoryRecord record = categoriesById.get(categoryId);
        if (record == null) {
            throw new NotFoundException("Category not found");
        }
        return record;
    }

    @Override
    public List<MoodleCategoryRecord> getCategories() {
        if (!"mock".equalsIgnoreCase(properties.getMode())) {
            throw new IntegrationException("Mock Moodle client is disabled");
        }
        return categoriesById.values().stream()
                .sorted((left, right) -> {
                    int sortCompare = Integer.compare(
                            left.sortOrder() == null ? Integer.MAX_VALUE : left.sortOrder(),
                            right.sortOrder() == null ? Integer.MAX_VALUE : right.sortOrder()
                    );
                    if (sortCompare != 0) {
                        return sortCompare;
                    }
                    return Long.compare(left.id(), right.id());
                })
                .toList();
    }

    @Override
    public List<MoodleCourseRecord> getCourses() {
        if (!"mock".equalsIgnoreCase(properties.getMode())) {
            throw new IntegrationException("Mock Moodle client is disabled");
        }
        Set<Long> courseIds = new LinkedHashSet<>();
        courseIds.add(501L);
        courseIds.addAll(coursesById.keySet());
        return courseIds.stream()
                .map(this::getCourseById)
                .toList();
    }

    @Override
    public void validateCredentials(String username, String password) {
        if (!"mock".equalsIgnoreCase(properties.getMode())) {
            throw new IntegrationException("Mock Moodle client is disabled");
        }
        String normalizedUsername = username.toLowerCase();
        if (!usersByUsername.containsKey(normalizedUsername)) {
            throw new IntegrationException("Moodle user not found");
        }
        String storedPassword = passwordsByUsername.get(normalizedUsername);
        if (storedPassword == null || !storedPassword.equals(password)) {
            throw new IntegrationException("Invalid Moodle credentials");
        }
    }

    @Override
    public void updatePassword(Long moodleUserId, String newPassword) {
        if (!"mock".equalsIgnoreCase(properties.getMode())) {
            throw new IntegrationException("Mock Moodle client is disabled");
        }
        MoodleUserRecord record = usersByUsername.values()
                .stream()
                .filter(user -> user.id().equals(moodleUserId))
                .findFirst()
                .orElseThrow(() -> new IntegrationException("Moodle user not found"));
        passwordsByUsername.put(record.username().toLowerCase(), newPassword);
    }

    @Override
    public String createLoginUrl(String username, Long moodleUserId) {
        if (!"mock".equalsIgnoreCase(properties.getMode())) {
            throw new IntegrationException("Mock Moodle client is disabled");
        }
        String signature = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString((username + ":" + moodleUserId).getBytes(StandardCharsets.UTF_8));
        return properties.getLoginBaseUrl() + "/mock-login?username=" + username + "&token=" + signature;
    }

    @Override
    public List<MoodleCourseRecord> getUserCourses(Long moodleUserId) {
        if (!"mock".equalsIgnoreCase(properties.getMode())) {
            throw new IntegrationException("Mock Moodle client is disabled");
        }
        Set<Long> courseIds = new LinkedHashSet<>();
        courseIds.add(501L);
        courseIds.addAll(enrolledCoursesByMoodleUserId.getOrDefault(moodleUserId, Set.of()));
        return courseIds.stream()
                .map(this::getCourseById)
                .toList();
    }

    @Override
    public String createCourseLaunchUrl(Long courseId) {
        if (!"mock".equalsIgnoreCase(properties.getMode())) {
            throw new IntegrationException("Mock Moodle client is disabled");
        }
        return properties.getLoginBaseUrl() + "/course/view.php?id=" + courseId;
    }

    @Override
    public String getSiteInfo() {
        return "mock";
    }
}
