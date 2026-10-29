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
import com.carbonedge.backend.exception.ConflictException;
import com.carbonedge.backend.exception.IntegrationException;
import com.carbonedge.backend.exception.NotFoundException;
import com.carbonedge.backend.exception.UnauthorizedException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Slf4j
@Component
@ConditionalOnProperty(prefix = "moodle.integration", name = "mode", havingValue = "real", matchIfMissing = true)
public class RealMoodleClient implements MoodleClient {

    private final MoodleIntegrationProperties properties;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public RealMoodleClient(MoodleIntegrationProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.restClient = RestClient.builder().build();
    }

    @Override
    public MoodleUserRecord createUser(MoodleCreateUserRequest request) {
        requireServiceToken();

        log.info("Call Moodle with function : {}", MoodleWebServiceFunctions.CORE_USER_CREATE_USERS);

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("wstoken", properties.getServiceToken());
        form.add("wsfunction", MoodleWebServiceFunctions.CORE_USER_CREATE_USERS);
        form.add("moodlewsrestformat", "json");
        form.add("users[0][username]", request.username());
        form.add("users[0][password]", request.password());
        form.add("users[0][firstname]", request.firstName());
        form.add("users[0][lastname]", request.lastName());
        form.add("users[0][email]", request.email());
        form.add("users[0][auth]", "manual");

        JsonNode root = postForm(buildApiUrl(), form);
        ensureNoWebServiceError(root, true);
        if (!root.isArray() || root.isEmpty()) {
            throw new IntegrationException("Moodle create user returned an empty response");
        }

        JsonNode createdUser = root.get(0);
        return new MoodleUserRecord(
                createdUser.path("id").asLong(),
                createdUser.path("username").asText(request.username()),
                createdUser.path("email").asText(request.email())
        );
    }

    @Override
    public void updateUser(MoodleUpdateUserRequest request) {
        requireServiceToken();

        log.info("Call Moodle with function : {}", MoodleWebServiceFunctions.CORE_USER_UPDATE_USERS);

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("wstoken", properties.getServiceToken());
        form.add("wsfunction", MoodleWebServiceFunctions.CORE_USER_UPDATE_USERS);
        form.add("moodlewsrestformat", "json");
        form.add("users[0][id]", String.valueOf(request.id()));
        form.add("users[0][email]", request.email());
        form.add("users[0][firstname]", request.firstName());
        form.add("users[0][lastname]", request.lastName());

        JsonNode root = postForm(buildApiUrl(), form);
        ensureNoWebServiceError(root, false);
    }

    @Override
    public MoodleCourseRecord createCourse(MoodleCreateCourseRequest request) {
        requireServiceToken();

        log.info("Call Moodle with function : {}", MoodleWebServiceFunctions.CORE_COURSE_CREATE_COURSES);

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("wstoken", properties.getServiceToken());
        form.add("wsfunction", MoodleWebServiceFunctions.CORE_COURSE_CREATE_COURSES);
        form.add("moodlewsrestformat", "json");
        form.add("courses[0][fullname]", request.fullName());
        form.add("courses[0][shortname]", request.shortName());
        form.add("courses[0][categoryid]", String.valueOf(request.categoryId()));
        form.add("courses[0][visible]", request.visible() ? "1" : "0");
        if (StringUtils.hasText(request.summary())) {
            form.add("courses[0][summary]", request.summary());
            form.add("courses[0][summaryformat]", "1");
        }

        JsonNode root = postForm(buildApiUrl(), form);
        ensureNoWebServiceError(root, false);
        if (!root.isArray() || root.isEmpty()) {
            throw new IntegrationException("Moodle create course returned an empty response");
        }

        JsonNode createdCourse = root.get(0);
        return new MoodleCourseRecord(
                createdCourse.path("id").asLong(),
                createdCourse.path("shortname").asText(request.shortName()),
                request.fullName(),
                request.fullName(),
                request.categoryId(),
                request.summary() == null ? "" : request.summary()
        );
    }

    @Override
    public MoodleCategoryRecord createCategory(MoodleCreateCategoryRequest request) {
        requireServiceToken();

        log.info("Call Moodle with function : {}", MoodleWebServiceFunctions.CORE_COURSE_CREATE_CATEGORIES);

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("wstoken", properties.getServiceToken());
        form.add("wsfunction", MoodleWebServiceFunctions.CORE_COURSE_CREATE_CATEGORIES);
        form.add("moodlewsrestformat", "json");
        form.add("categories[0][name]", request.name());
        form.add("categories[0][parent]", String.valueOf(request.parent()));
        if (StringUtils.hasText(request.idNumber())) {
            form.add("categories[0][idnumber]", request.idNumber());
        }
        if (StringUtils.hasText(request.description())) {
            form.add("categories[0][description]", request.description());
            form.add("categories[0][descriptionformat]", "1");
        }
        if (StringUtils.hasText(request.theme())) {
            form.add("categories[0][theme]", request.theme());
        }

        JsonNode root = postForm(buildApiUrl(), form);
        ensureNoWebServiceError(root, false);
        Long categoryId = extractFirstId(root, "category");
        return getCategoryById(categoryId);
    }

    @Override
    public void enrollStudent(Long courseId, Long moodleUserId) {
        requireServiceToken();

        log.info("Call Moodle with function : {}", MoodleWebServiceFunctions.ENROL_MANUAL_ENROL_USERS);

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("wstoken", properties.getServiceToken());
        form.add("wsfunction", MoodleWebServiceFunctions.ENROL_MANUAL_ENROL_USERS);
        form.add("moodlewsrestformat", "json");
        form.add("enrolments[0][roleid]", "5");
        form.add("enrolments[0][userid]", String.valueOf(moodleUserId));
        form.add("enrolments[0][courseid]", String.valueOf(courseId));
        form.add("enrolments[0][suspend]", "0");

        JsonNode root = postForm(buildApiUrl(), form);
        ensureNoWebServiceError(root, false);
    }

    @Override
    public void unenrollStudent(Long courseId, Long moodleUserId) {
        requireServiceToken();

        log.info("Call Moodle with function : {}", MoodleWebServiceFunctions.ENROL_MANUAL_UNENROL_USERS);

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("wstoken", properties.getServiceToken());
        form.add("wsfunction", MoodleWebServiceFunctions.ENROL_MANUAL_UNENROL_USERS);
        form.add("moodlewsrestformat", "json");
        form.add("enrolments[0][roleid]", "5");
        form.add("enrolments[0][userid]", String.valueOf(moodleUserId));
        form.add("enrolments[0][courseid]", String.valueOf(courseId));

        JsonNode root = postForm(buildApiUrl(), form);
        ensureNoWebServiceError(root, false);
    }

    @Override
    public MoodleCourseRecord updateCourse(MoodleUpdateCourseRequest request) {
        requireServiceToken();

        log.info("Call Moodle with function : {}", MoodleWebServiceFunctions.CORE_COURSE_UPDATE_COURSES);

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("wstoken", properties.getServiceToken());
        form.add("wsfunction", MoodleWebServiceFunctions.CORE_COURSE_UPDATE_COURSES);
        form.add("moodlewsrestformat", "json");
        form.add("courses[0][id]", String.valueOf(request.id()));
        if (StringUtils.hasText(request.shortName())) {
            form.add("courses[0][shortname]", request.shortName());
        }
        if (StringUtils.hasText(request.fullName())) {
            form.add("courses[0][fullname]", request.fullName());
        }
        if (request.categoryId() != null) {
            form.add("courses[0][categoryid]", String.valueOf(request.categoryId()));
        }
        if (request.visible() != null) {
            form.add("courses[0][visible]", request.visible() ? "1" : "0");
        }
        if (request.summary() != null) {
            form.add("courses[0][summary]", request.summary());
            form.add("courses[0][summaryformat]", "1");
        }

        JsonNode root = postForm(buildApiUrl(), form);
        ensureNoWebServiceError(root, false);
        return getCourseById(request.id());
    }

    @Override
    public MoodleCategoryRecord updateCategory(MoodleUpdateCategoryRequest request) {
        requireServiceToken();

        log.info("Call Moodle with function : {}", MoodleWebServiceFunctions.CORE_COURSE_UPDATE_CATEGORIES);

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("wstoken", properties.getServiceToken());
        form.add("wsfunction", MoodleWebServiceFunctions.CORE_COURSE_UPDATE_CATEGORIES);
        form.add("moodlewsrestformat", "json");
        form.add("categories[0][id]", String.valueOf(request.id()));
        if (StringUtils.hasText(request.name())) {
            form.add("categories[0][name]", request.name());
        }
        if (request.parent() != null) {
            form.add("categories[0][parent]", String.valueOf(request.parent()));
        }
        if (StringUtils.hasText(request.idNumber())) {
            form.add("categories[0][idnumber]", request.idNumber());
        }
        if (StringUtils.hasText(request.description())) {
            form.add("categories[0][description]", request.description());
            form.add("categories[0][descriptionformat]", "1");
        }
        if (StringUtils.hasText(request.theme())) {
            form.add("categories[0][theme]", request.theme());
        }

        JsonNode root = postForm(buildApiUrl(), form);
        ensureNoWebServiceError(root, false);
        return getCategoryById(request.id());
    }

    @Override
    public void deleteCourse(Long courseId) {
        requireServiceToken();

        log.info("Call Moodle with function : {}", MoodleWebServiceFunctions.CORE_COURSE_DELETE_COURSES);

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("wstoken", properties.getServiceToken());
        form.add("wsfunction", MoodleWebServiceFunctions.CORE_COURSE_DELETE_COURSES);
        form.add("moodlewsrestformat", "json");
        form.add("courseids[0]", String.valueOf(courseId));

        JsonNode root = postForm(buildApiUrl(), form);
        ensureNoWebServiceError(root, false);
    }

    @Override
    public void deleteCategory(Long categoryId) {
        requireServiceToken();

        log.info("Call Moodle with function : {}", MoodleWebServiceFunctions.CORE_COURSE_DELETE_CATEGORIES);

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("wstoken", properties.getServiceToken());
        form.add("wsfunction", MoodleWebServiceFunctions.CORE_COURSE_DELETE_CATEGORIES);
        form.add("moodlewsrestformat", "json");
        form.add("categories[0][id]", String.valueOf(categoryId));
        form.add("categories[0][newparent]", "0");
        form.add("categories[0][recursive]", "1");

        JsonNode root = postForm(buildApiUrl(), form);
        ensureNoWebServiceError(root, false);
    }

    @Override
    public MoodleCourseRecord getCourseById(Long courseId) {
        requireServiceToken();

        log.info("Call Moodle with function : {}", MoodleWebServiceFunctions.CORE_COURSE_GET_COURSES_BY_FIELD);

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("wstoken", properties.getServiceToken());
        form.add("wsfunction", MoodleWebServiceFunctions.CORE_COURSE_GET_COURSES_BY_FIELD);
        form.add("moodlewsrestformat", "json");
        form.add("field", "id");
        form.add("value", String.valueOf(courseId));

        JsonNode root = postForm(buildApiUrl(), form);
        ensureNoWebServiceError(root, false);

        JsonNode courses = root.path("courses");
        if (!courses.isArray() || courses.isEmpty()) {
            throw new IntegrationException("Moodle course not found");
        }

        JsonNode course = courses.get(0);
        return new MoodleCourseRecord(
                course.path("id").asLong(),
                course.path("shortname").asText(""),
                course.path("fullname").asText(""),
                course.path("displayname").asText(course.path("fullname").asText("")),
                course.path("categoryid").isMissingNode() ? null : course.path("categoryid").asInt(),
                course.path("summary").asText("")
        );
    }

    @Override
    public MoodleCategoryRecord getCategoryById(Long categoryId) {
        requireServiceToken();

        log.info("Call Moodle with function : {}", MoodleWebServiceFunctions.CORE_COURSE_GET_CATEGORIES);

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("wstoken", properties.getServiceToken());
        form.add("wsfunction", MoodleWebServiceFunctions.CORE_COURSE_GET_CATEGORIES);
        form.add("moodlewsrestformat", "json");
        form.add("criteria[0][key]", "id");
        form.add("criteria[0][value]", String.valueOf(categoryId));
        form.add("addsubcategories", "0");

        JsonNode root = postForm(buildApiUrl(), form);
        ensureNoWebServiceError(root, false);
        if (!root.isArray() || root.isEmpty()) {
            throw new NotFoundException("Category not found");
        }
        return mapCategory(root.get(0));
    }

    @Override
    public List<MoodleCategoryRecord> getCategories() {
        requireServiceToken();

        log.info("Call Moodle with function : {}", MoodleWebServiceFunctions.CORE_COURSE_GET_CATEGORIES);

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("wstoken", properties.getServiceToken());
        form.add("wsfunction", MoodleWebServiceFunctions.CORE_COURSE_GET_CATEGORIES);
        form.add("moodlewsrestformat", "json");

        JsonNode root = postForm(buildApiUrl(), form);
        ensureNoWebServiceError(root, false);
        if (!root.isArray()) {
            throw new IntegrationException("Moodle categories response is not a list");
        }

        List<MoodleCategoryRecord> categories = new ArrayList<>();
        for (JsonNode category : root) {
            categories.add(mapCategory(category));
        }
        return categories;
    }

    @Override
    public List<MoodleCourseRecord> getCourses() {
        requireServiceToken();

        log.info("Call Moodle with function : {}", "core_course_get_courses");

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("wstoken", properties.getServiceToken());
        form.add("wsfunction", "core_course_get_courses");
        form.add("moodlewsrestformat", "json");

        JsonNode root = postForm(buildApiUrl(), form);
        ensureNoWebServiceError(root, false);
        if (!root.isArray()) {
            throw new IntegrationException("Moodle courses response is not a list");
        }

        List<MoodleCourseRecord> courses = new ArrayList<>();
        for (JsonNode course : root) {
            courses.add(new MoodleCourseRecord(
                    course.path("id").asLong(),
                    course.path("shortname").asText(""),
                    course.path("fullname").asText(""),
                    course.path("displayname").asText(course.path("fullname").asText("")),
                    course.path("categoryid").isMissingNode() ? null : course.path("categoryid").asInt(),
                    course.path("summary").asText("")
            ));
        }
        return courses;
    }

    @Override
    public void validateCredentials(String username, String password) {
        String loginService = properties.getLoginService();
        if (!StringUtils.hasText(loginService)) {
            throw new IntegrationException("MOODLE_LOGIN_SERVICE is required for real integration mode");
        }

        log.info("Moodle validate credential for user='{}' len={}",
                username,
                username.length());
        log.info("Call Moodle with function : {}", "login/token.php");

        log.info("Moodle validate credential endpoint: {}", buildTokenUrl());
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("username", username);
        form.add("password", password);
        form.add("service", loginService);
        JsonNode root;
        try {
            String response = restClient.post()
                    .uri(buildTokenUrl())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(String.class);
            root = parseJson(response);
        } catch (RestClientException exception) {
            throw new IntegrationException("Failed to call Moodle token endpoint: " + exception.getMessage());
        }
        if (root.hasNonNull("token") || root.hasNonNull("privatetoken")) {
            return;
        }

        String error = root.path("error").asText("");
        String errorCode = root.path("errorcode").asText("");
        String message = firstNonBlank(error, errorCode, "Moodle login validation failed");
        String normalized = message.toLowerCase();
        if (normalized.contains("invalidlogin") || normalized.contains("invalid login")) {
            throw new UnauthorizedException("Invalid credentials");
        }
        throw new IntegrationException(message);
    }

    @Override
    public void updatePassword(Long moodleUserId, String newPassword) {
        requireServiceToken();

        log.info("Call Moodle with function : {}", MoodleWebServiceFunctions.CORE_USER_UPDATE_USERS);

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("wstoken", properties.getServiceToken());
        form.add("wsfunction", MoodleWebServiceFunctions.CORE_USER_UPDATE_USERS);
        form.add("moodlewsrestformat", "json");
        form.add("users[0][id]", String.valueOf(moodleUserId));
        form.add("users[0][password]", newPassword);
        form.add("users[0][auth]", "manual");

        JsonNode root = postForm(buildApiUrl(), form);
        ensureNoWebServiceError(root, false);
        if (root.isObject() && root.has("exception")) {
            String message = root.path("message").asText("Moodle password update failed");
            throw new IntegrationException(message);
        }
    }

    @Override
    public String createLoginUrl(String username, Long moodleUserId) {
        String wantsUrl = normalizeBaseUrl(properties.getLoginBaseUrl()) + "/my/";
        return normalizeBaseUrl(properties.getLoginBaseUrl())
                + "/login/index.php?wantsurl="
                + encode(wantsUrl);
    }

    @Override
    public List<MoodleCourseRecord> getUserCourses(Long moodleUserId) {
        requireServiceToken();

        log.info("Call Moodle with function : {}", MoodleWebServiceFunctions.CORE_ENROL_GET_USERS_COURSES);

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("wstoken", properties.getServiceToken());
        form.add("wsfunction", MoodleWebServiceFunctions.CORE_ENROL_GET_USERS_COURSES);
        form.add("moodlewsrestformat", "json");
        form.add("userid", String.valueOf(moodleUserId));

        JsonNode root = postForm(buildApiUrl(), form);
        ensureNoWebServiceError(root, false);
        if (!root.isArray()) {
            throw new IntegrationException("Moodle courses response is not a list");
        }

        List<MoodleCourseRecord> courses = new ArrayList<>();
        for (JsonNode course : root) {
            courses.add(new MoodleCourseRecord(
                    course.path("id").asLong(),
                    course.path("shortname").asText(""),
                    course.path("fullname").asText(""),
                    course.path("displayname").asText(course.path("fullname").asText("")),
                    course.path("categoryid").isMissingNode() ? null : course.path("categoryid").asInt(),
                    course.path("summary").asText("")
            ));
        }
        return courses;
    }

    @Override
    public String createCourseLaunchUrl(Long courseId) {
        return normalizeBaseUrl(properties.getLoginBaseUrl()) + "/course/view.php?id=" + courseId;
    }

    @Override
    public String getSiteInfo() {
        log.info("Call Moodle with function : {}", MoodleWebServiceFunctions.CORE_WEBSERVICE_GET_SITE_INFO);

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("wstoken", properties.getServiceToken());
        form.add("wsfunction", MoodleWebServiceFunctions.CORE_WEBSERVICE_GET_SITE_INFO);
        form.add("moodlewsrestformat", "json");
        JsonNode root = postForm(buildApiUrl(), form);
        return root.toString();
    }

    private JsonNode postForm(String url, MultiValueMap<String, String> form) {
        try {
            String response = restClient.post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(String.class);
            return parseJson(response);
        } catch (RestClientException exception) {
            throw new IntegrationException("Failed to call Moodle web service: " + exception.getMessage());
        }
    }

    private JsonNode getJson(String url) {
        try {
            String response = restClient.get()
                    .uri(url)
                    .retrieve()
                    .body(String.class);
            return parseJson(response);
        } catch (RestClientException exception) {
            throw new IntegrationException("Failed to call Moodle token endpoint: " + exception.getMessage());
        }
    }

    private JsonNode parseJson(String response) {
        try {
            return objectMapper.readTree(response == null ? "{}" : response);
        } catch (IOException exception) {
            throw new IntegrationException("Received invalid JSON from Moodle");
        }
    }

    private void ensureNoWebServiceError(JsonNode root, boolean createUserCall) {
        if (!root.isObject() || !root.has("exception")) {
            return;
        }

        String message = root.path("message").asText("Moodle web service error");
        String normalized = message.toLowerCase();
        if (createUserCall && (normalized.contains("already exists") || normalized.contains("duplicate"))) {
            throw new ConflictException(message);
        }
        throw new IntegrationException(message);
    }

    private Long extractFirstId(JsonNode root, String fallbackEntityName) {
        if (root.isArray() && !root.isEmpty()) {
            JsonNode first = root.get(0);
            if (first.hasNonNull("id")) {
                return first.path("id").asLong();
            }
        }
        if (root.isObject() && root.hasNonNull("id")) {
            return root.path("id").asLong();
        }
        throw new IntegrationException("Moodle " + fallbackEntityName + " creation returned an empty response");
    }

    private MoodleCategoryRecord mapCategory(JsonNode category) {
        return new MoodleCategoryRecord(
                category.path("id").asLong(),
                category.path("name").asText(""),
                category.hasNonNull("idnumber") ? category.path("idnumber").asText(null) : null,
                category.path("description").asText(""),
                category.path("parent").isMissingNode() ? null : category.path("parent").asLong(),
                category.path("sortorder").isMissingNode() ? null : category.path("sortorder").asInt(),
                category.path("coursecount").isMissingNode() ? null : category.path("coursecount").asInt(),
                category.path("visible").isMissingNode() ? null : category.path("visible").asInt() == 1,
                category.path("visibleold").isMissingNode() ? null : category.path("visibleold").asInt() == 1,
                category.path("timemodified").isMissingNode() ? null : category.path("timemodified").asLong(),
                category.path("depth").isMissingNode() ? null : category.path("depth").asInt(),
                category.path("path").asText(""),
                category.hasNonNull("theme") ? category.path("theme").asText(null) : null
        );
    }

    private void requireServiceToken() {
        if (!StringUtils.hasText(properties.getServiceToken())) {
            throw new IntegrationException("MOODLE_SERVICE_TOKEN is required for real integration mode");
        }
    }

    private String buildApiUrl() {
        return normalizeBaseUrl(properties.getBaseUrl()) + "/webservice/rest/server.php";
    }

    private String buildTokenUrl() {
        return normalizeBaseUrl(properties.getBaseUrl()) + "/login/token.php";
    }

    private String normalizeBaseUrl(String baseUrl) {
        if (!StringUtils.hasText(baseUrl)) {
            throw new IntegrationException("Moodle base URL is not configured");
        }
        return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String firstNonBlank(String first, String second, String fallback) {
        if (StringUtils.hasText(first)) {
            return first;
        }
        if (StringUtils.hasText(second)) {
            return second;
        }
        return fallback;
    }
}
