package com.carbonedge.backend.integration.moodle;

import com.carbonedge.backend.client.moodle.MoodleClient;
import com.carbonedge.backend.config.MoodleIntegrationProperties;
import com.carbonedge.backend.dto.moodle.MoodleCreateUserRequest;
import com.carbonedge.backend.dto.moodle.MoodleCourseRecord;
import com.carbonedge.backend.dto.moodle.MoodleUserRecord;
import com.carbonedge.backend.exception.ConflictException;
import com.carbonedge.backend.exception.IntegrationException;
import com.carbonedge.backend.exception.UnauthorizedException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

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

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("wstoken", properties.getServiceToken());
        form.add("wsfunction", "core_user_create_users");
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
    public void validateCredentials(String username, String password) {
        String loginService = properties.getLoginService();
        if (!StringUtils.hasText(loginService)) {
            throw new IntegrationException("MOODLE_LOGIN_SERVICE is required for real integration mode");
        }

        String url = buildTokenUrl()
                + "?username=" + encode(username)
                + "&password=" + encode(password)
                + "&service=" + encode(loginService);

        JsonNode root = getJson(url);
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
    public String createLoginUrl(String username, Long moodleUserId) {
        String wantsUrl = normalizeBaseUrl(properties.getLoginBaseUrl()) + "/my/";
        return normalizeBaseUrl(properties.getLoginBaseUrl())
                + "/login/index.php?wantsurl="
                + encode(wantsUrl);
    }

    @Override
    public List<MoodleCourseRecord> getUserCourses(Long moodleUserId) {
        requireServiceToken();

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("wstoken", properties.getServiceToken());
        form.add("wsfunction", "core_enrol_get_users_courses");
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
