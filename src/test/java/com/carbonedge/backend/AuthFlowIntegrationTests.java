package com.carbonedge.backend;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class AuthFlowIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void registerLoginAndFetchCurrentUser() throws Exception {
        String registerPayload = """
                {
                  "username": "alice",
                  "email": "alice@example.com",
                  "password": "Password123",
                  "firstName": "Alice",
                  "lastName": "Nguyen"
                }
                """;

        MvcResult registerResult = mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerPayload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").isString())
                .andExpect(jsonPath("$.user.username").value("alice"))
                .andExpect(jsonPath("$.user.moodleUserId").isNumber())
                .andReturn();

        String registerToken = extractToken(registerResult);

        mockMvc.perform(get("/users/me")
                        .header("Authorization", "Bearer " + registerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("alice@example.com"))
                .andExpect(jsonPath("$.moodleUserId").isNumber());

        mockMvc.perform(get("/courses/my")
                        .header("Authorization", "Bearer " + registerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].id").value(501));

        mockMvc.perform(get("/courses/501")
                        .header("Authorization", "Bearer " + registerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(501))
                .andExpect(jsonPath("$.fullName").value("Sample Course 501"));

        mockMvc.perform(get("/users/me/dashboard")
                        .header("Authorization", "Bearer " + registerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.username").value("alice"))
                .andExpect(jsonPath("$.totalCourses").value(1))
                .andExpect(jsonPath("$.courses").isArray());

        MvcResult launchResult = mockMvc.perform(get("/courses/501/launch")
                        .header("Authorization", "Bearer " + registerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.courseId").value(501))
                .andExpect(jsonPath("$.launchUrl").value(org.hamcrest.Matchers.containsString("/local/lmslaunch/launch.php?token=")))
                .andReturn();

        String launchUrl = objectMapper.readTree(launchResult.getResponse().getContentAsString()).get("launchUrl").asText();
        String encodedLaunchToken = launchUrl.substring(launchUrl.indexOf("token=") + 6);
        String launchToken = new String(java.util.Base64.getUrlDecoder().decode(encodedLaunchToken), java.nio.charset.StandardCharsets.UTF_8);

        mockMvc.perform(post("/moodle/launch/consume")
                        .header("X-Lms-Launch-Secret", "test-shared-secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "token": "%s"
                                }
                                """.formatted(launchToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.moodleUserId").isNumber())
                .andExpect(jsonPath("$.courseId").value(501))
                .andExpect(jsonPath("$.redirectPath").value("/course/view.php?id=501"));

        mockMvc.perform(get("/courses/999")
                        .header("Authorization", "Bearer " + registerToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        mockMvc.perform(get("/courses/999/launch")
                        .header("Authorization", "Bearer " + registerToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        String loginPayload = """
                {
                  "usernameOrEmail": "alice",
                  "password": "Password123"
                }
                """;

        MvcResult loginResult = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.username").value("alice"))
                .andReturn();

        String loginToken = extractToken(loginResult);

        mockMvc.perform(post("/auth/moodle-session")
                        .header("Authorization", "Bearer " + loginToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.moodleLoginUrl").value(org.hamcrest.Matchers.containsString("/local/lmslaunch/launch.php?token=")));
    }

    @Test
    void duplicateEmailReturnsConflict() throws Exception {
        String payload = """
                {
                  "username": "bob",
                  "email": "bob@example.com",
                  "password": "Password123",
                  "firstName": "Bob",
                  "lastName": "Lee"
                }
                """;

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload.replace("\"bob\"", "\"bob2\"")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
    }

    @Test
    void loginWithEmailWorks() throws Exception {
        String registerPayload = """
                {
                  "username": "charlie",
                  "email": "charlie@example.com",
                  "password": "Password123",
                  "firstName": "Charlie",
                  "lastName": "Tan"
                }
                """;

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerPayload))
                .andExpect(status().isCreated());

        String loginPayload = """
                {
                  "usernameOrEmail": "charlie@example.com",
                  "password": "Password123"
                }
                """;

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.username").value("charlie"))
                .andExpect(jsonPath("$.user.email").value("charlie@example.com"));
    }

    private String extractToken(MvcResult result) throws Exception {
        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        return json.get("accessToken").asText();
    }
}
