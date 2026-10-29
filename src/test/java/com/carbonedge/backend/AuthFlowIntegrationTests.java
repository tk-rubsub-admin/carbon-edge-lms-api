package com.carbonedge.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.carbonedge.backend.service.EmailService;
import com.carbonedge.backend.model.UserAccount;
import com.carbonedge.backend.model.UserRole;
import com.carbonedge.backend.repository.UserAccountRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.io.ByteArrayOutputStream;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.autoconfigure.mail.MailProperties;
import org.springframework.http.MediaType;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class AuthFlowIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @MockBean
    private EmailService emailService;

    @MockBean
    private JavaMailSender javaMailSender;

    @MockBean
    private MailProperties mailProperties;

    @BeforeEach
    void resetEmailMock() {
        reset(emailService);
    }

    @Test
    void registerLoginAndFetchCurrentUser() throws Exception {
        String registerPayload = """
                {
                  "username": "alice",
                  "email": "alice@example.com",
                  "password": "Password123!",
                  "firstName": "Alice",
                  "lastName": "Nguyen",
                  "title": "นางสาว",
                  "phoneNumber": "0812345678",
                  "addressHouseNumber": "99/1",
                  "addressMoo": "3",
                  "addressBuilding": "Carbon Village",
                  "addressAlley": "สุขุมวิท 1",
                  "addressRoad": "สุขุมวิท",
                  "addressProvince": "กรุงเทพมหานคร",
                  "addressDistrict": "คลองเตย",
                  "addressSubdistrict": "คลองเตย",
                  "addressPostalCode": "10110",
                  "pdpaConsent": true,
                  "informationCertified": true
                }
                """;

        MvcResult registerResult = mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerPayload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").isNumber())
                .andExpect(jsonPath("$.email").value("alice@example.com"))
                .andExpect(jsonPath("$.verificationEmailSent").value(true))
                .andReturn();

        String verificationToken = extractTokenFromLatestEmail("alice@example.com");
        String verificationEmail = objectMapper.readTree(registerResult.getResponse().getContentAsString())
                .get("email")
                .asText();
        verify(emailService, times(1)).sendHtmlEmail(eq(verificationEmail), anyString(), anyString(), anyString());

        MvcResult verifyResult = mockMvc.perform(get("/auth/verify-email")
                        .param("token", verificationToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isString())
                .andExpect(jsonPath("$.user.username").value("alice"))
                .andExpect(jsonPath("$.user.moodleUserId").isNumber())
                .andReturn();

        String registerToken = extractToken(verifyResult);

        UserAccount alice = userAccountRepository.findByUsernameIgnoreCaseOrEmailIgnoreCase("alice", "alice")
                .orElseThrow();
        assertThat(alice.getTitle()).isEqualTo("นางสาว");
        assertThat(alice.getPhoneNumber()).isEqualTo("0812345678");
        assertThat(alice.getAddressHouseNumber()).isEqualTo("99/1");
        assertThat(alice.getAddressMoo()).isEqualTo("3");
        assertThat(alice.getAddressBuilding()).isEqualTo("Carbon Village");
        assertThat(alice.getAddressAlley()).isEqualTo("สุขุมวิท 1");
        assertThat(alice.getAddressRoad()).isEqualTo("สุขุมวิท");
        assertThat(alice.getAddressProvince()).isEqualTo("กรุงเทพมหานคร");
        assertThat(alice.getAddressDistrict()).isEqualTo("คลองเตย");
        assertThat(alice.getAddressSubdistrict()).isEqualTo("คลองเตย");
        assertThat(alice.getAddressPostalCode()).isEqualTo("10110");
        assertThat(alice.getPdpaConsentAt()).isNotNull();
        assertThat(alice.getInformationCertifiedAt()).isNotNull();
        assertThat(alice.isFirstLogin()).isFalse();
        assertThat(alice.isCompletedProfile()).isTrue();
        alice.setRole(UserRole.ADMIN);
        userAccountRepository.save(alice);

        mockMvc.perform(get("/users/me")
                        .header("Authorization", "Bearer " + registerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("alice@example.com"))
                .andExpect(jsonPath("$.role").value("ADMIN"))
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
                  "password": "Password123!"
                }
                """;

        MvcResult loginResult = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.username").value("alice"))
                .andExpect(jsonPath("$.user.role").value("ADMIN"))
                .andReturn();

        String loginToken = extractToken(loginResult);

        mockMvc.perform(post("/courses")
                        .header("Authorization", "Bearer " + loginToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "shortName": "CS101",
                                  "fullName": "Introduction to Computer Science",
                                  "categoryId": 12,
                                  "summary": "Basic programming and algorithms",
                                  "visible": true
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.shortName").value("CS101"))
                .andExpect(jsonPath("$.fullName").value("Introduction to Computer Science"))
                .andExpect(jsonPath("$.displayName").value("Introduction to Computer Science"))
                .andExpect(jsonPath("$.categoryId").value(12))
                .andExpect(jsonPath("$.summary").value("Basic programming and algorithms"));

        mockMvc.perform(patch("/courses/5001")
                        .header("Authorization", "Bearer " + loginToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fullName": "Introduction to CS Updated",
                                  "summary": "Updated summary",
                                  "visible": false
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(5001))
                .andExpect(jsonPath("$.fullName").value("Introduction to CS Updated"))
                .andExpect(jsonPath("$.summary").value("Updated summary"));

        mockMvc.perform(delete("/courses/5001")
                        .header("Authorization", "Bearer " + loginToken))
                .andExpect(status().isNoContent());

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
                  "password": "Password123!",
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
                  "password": "Password123!",
                  "firstName": "Charlie",
                  "lastName": "Tan"
                }
                """;

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerPayload))
                .andExpect(status().isCreated());

        String verificationToken = extractTokenFromLatestEmail("charlie@example.com");
        mockMvc.perform(get("/auth/verify-email")
                        .param("token", verificationToken))
                .andExpect(status().isOk());

        String loginPayload = """
                {
                  "usernameOrEmail": "charlie@example.com",
                  "password": "Password123!"
                }
                """;

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.username").value("charlie"))
                .andExpect(jsonPath("$.user.email").value("charlie@example.com"));
    }

    @Test
    void resendVerificationSendsAnotherMail() throws Exception {
        String registerPayload = """
                {
                  "username": "diana",
                  "email": "diana@example.com",
                  "password": "Password123!",
                  "firstName": "Diana",
                  "lastName": "Ross"
                }
                """;

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerPayload))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/auth/resend-verification")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "diana@example.com"
                                }
                                """))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.sent").value(true))
                .andExpect(jsonPath("$.expiresAt").isNotEmpty());

        verify(emailService, times(2)).sendHtmlEmail(eq("diana@example.com"), anyString(), anyString(), anyString());
    }

    @Test
    void forgotPasswordAndResetPasswordWork() throws Exception {
        String registerPayload = """
                {
                  "username": "erin",
                  "email": "erin@example.com",
                  "password": "Password123!",
                  "firstName": "Erin",
                  "lastName": "Mills"
                }
                """;

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerPayload))
                .andExpect(status().isCreated());

        String verificationToken = extractTokenFromLatestEmail("erin@example.com");
        mockMvc.perform(get("/auth/verify-email")
                        .param("token", verificationToken))
                .andExpect(status().isOk());

        mockMvc.perform(post("/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "erin@example.com"
                                }
                                """))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.sent").value(true))
                .andExpect(jsonPath("$.expiresAt").isNotEmpty());

        String resetToken = extractTokenFromLatestEmail("erin@example.com");

        mockMvc.perform(post("/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "token": "%s",
                                  "newPassword": "NewPassword123!"
                                }
                                """.formatted(resetToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.username").value("erin"))
                .andExpect(jsonPath("$.accessToken").isString());

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "usernameOrEmail": "erin@example.com",
                                  "password": "NewPassword123!"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.username").value("erin"));
    }

    @Test
    void weakPasswordIsRejectedByValidation() throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "weakuser",
                                  "email": "weakuser@example.com",
                                  "password": "Password123",
                                  "firstName": "Weak",
                                  "lastName": "User"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.validationErrors.password").exists());
    }

    @Test
    void nonAdminCannotManageCourses() throws Exception {
        String registerPayload = """
                {
                  "username": "frank",
                  "email": "frank@example.com",
                  "password": "Password123!",
                  "firstName": "Frank",
                  "lastName": "Stone"
                }
                """;

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerPayload))
                .andExpect(status().isCreated());

        String verificationToken = extractTokenFromLatestEmail("frank@example.com");
        mockMvc.perform(get("/auth/verify-email")
                        .param("token", verificationToken))
                .andExpect(status().isOk());

        MvcResult loginResult = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "usernameOrEmail": "frank",
                                  "password": "Password123!"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();

        String loginToken = extractToken(loginResult);

        mockMvc.perform(post("/courses")
                        .header("Authorization", "Bearer " + loginToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "shortName": "CS200",
                                  "fullName": "Non-admin attempt",
                                  "categoryId": 12
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void teacherCanEditCourseButCannotCreateOrDelete() throws Exception {
        String adminRegisterPayload = """
                {
                  "username": "harry",
                  "email": "harry@example.com",
                  "password": "Password123!",
                  "firstName": "Harry",
                  "lastName": "Cole"
                }
                """;

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(adminRegisterPayload))
                .andExpect(status().isCreated());

        String adminVerificationToken = extractTokenFromLatestEmail("harry@example.com");
        mockMvc.perform(get("/auth/verify-email")
                        .param("token", adminVerificationToken))
                .andExpect(status().isOk());

        UserAccount harry = userAccountRepository.findByUsernameIgnoreCaseOrEmailIgnoreCase("harry", "harry")
                .orElseThrow();
        harry.setRole(UserRole.ADMIN);
        userAccountRepository.save(harry);

        MvcResult adminLoginResult = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "usernameOrEmail": "harry",
                                  "password": "Password123!"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.role").value("ADMIN"))
                .andReturn();

        String adminToken = extractToken(adminLoginResult);

        mockMvc.perform(post("/courses")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "shortName": "CS200",
                                  "fullName": "Teacher editable course",
                                  "categoryId": 12,
                                  "summary": "Initial summary",
                                  "visible": true
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(5001));

        String teacherRegisterPayload = """
                {
                  "username": "iris",
                  "email": "iris@example.com",
                  "password": "Password123!",
                  "firstName": "Iris",
                  "lastName": "Mann"
                }
                """;

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(teacherRegisterPayload))
                .andExpect(status().isCreated());

        String teacherVerificationToken = extractTokenFromLatestEmail("iris@example.com");
        mockMvc.perform(get("/auth/verify-email")
                        .param("token", teacherVerificationToken))
                .andExpect(status().isOk());

        UserAccount iris = userAccountRepository.findByUsernameIgnoreCaseOrEmailIgnoreCase("iris", "iris")
                .orElseThrow();
        iris.setRole(UserRole.ADMIN);
        userAccountRepository.save(iris);

        mockMvc.perform(post("/courses/5001/teachers/" + iris.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());

        MvcResult teacherLoginResult = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "usernameOrEmail": "iris",
                                  "password": "Password123!"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.role").value("ADMIN"))
                .andReturn();

        String teacherToken = extractToken(teacherLoginResult);

        mockMvc.perform(get("/courses/my")
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[*].id").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem(5001))));

        mockMvc.perform(get("/courses/5001/teachers")
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].username").value("iris"))
                .andExpect(jsonPath("$[0].role").value("ADMIN"));

        mockMvc.perform(patch("/courses/5001")
                        .header("Authorization", "Bearer " + teacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fullName": "Teacher edited course",
                                  "summary": "Updated by teacher"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(5001))
                .andExpect(jsonPath("$.fullName").value("Teacher edited course"))
                .andExpect(jsonPath("$.summary").value("Updated by teacher"));

        String studentRegisterPayload = """
                {
                  "username": "kate",
                  "email": "kate@example.com",
                  "password": "Password123!",
                  "firstName": "Kate",
                  "lastName": "Ng"
                }
                """;

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(studentRegisterPayload))
                .andExpect(status().isCreated());

        String studentVerificationToken = extractTokenFromLatestEmail("kate@example.com");
        mockMvc.perform(get("/auth/verify-email")
                        .param("token", studentVerificationToken))
                .andExpect(status().isOk());

        MvcResult studentLoginResult = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "usernameOrEmail": "kate",
                                  "password": "Password123!"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.role").value("STUDENT"))
                .andReturn();

        String studentToken = extractToken(studentLoginResult);

        UserAccount kate = userAccountRepository.findByUsernameIgnoreCaseOrEmailIgnoreCase("kate", "kate")
                .orElseThrow();

        mockMvc.perform(post("/courses/5001/students/" + kate.getId())
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/courses/5001/students")
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items[0].username").value("kate"))
                .andExpect(jsonPath("$.items[0].role").value("STUDENT"));

        mockMvc.perform(get("/courses/my")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[*].id").value(org.hamcrest.Matchers.hasItem(5001)));

        mockMvc.perform(delete("/courses/5001/students/" + kate.getId())
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/courses/5001/students")
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items").isEmpty());

        mockMvc.perform(get("/courses/my")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[*].id").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem(5001))));

        String unassignedTeacherPayload = """
                {
                  "username": "jane",
                  "email": "jane@example.com",
                  "password": "Password123!",
                  "firstName": "Jane",
                  "lastName": "Doe"
                }
                """;

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(unassignedTeacherPayload))
                .andExpect(status().isCreated());

        String janeVerificationToken = extractTokenFromLatestEmail("jane@example.com");
        mockMvc.perform(get("/auth/verify-email")
                        .param("token", janeVerificationToken))
                .andExpect(status().isOk());

        UserAccount jane = userAccountRepository.findByUsernameIgnoreCaseOrEmailIgnoreCase("jane", "jane")
                .orElseThrow();
        jane.setRole(UserRole.STUDENT);
        userAccountRepository.save(jane);

        MvcResult janeLoginResult = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "usernameOrEmail": "jane",
                                  "password": "Password123!"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.role").value("STUDENT"))
                .andReturn();

        String janeToken = extractToken(janeLoginResult);

        mockMvc.perform(patch("/courses/5001")
                        .header("Authorization", "Bearer " + janeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fullName": "Student update attempt"
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        mockMvc.perform(get("/courses/5001/teachers")
                        .header("Authorization", "Bearer " + janeToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        mockMvc.perform(post("/courses")
                        .header("Authorization", "Bearer " + janeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "shortName": "CS201",
                                  "fullName": "Student create attempt",
                                  "categoryId": 12
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        mockMvc.perform(delete("/courses/5001")
                        .header("Authorization", "Bearer " + janeToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void adminCanManageCourseContentAndStudentsCanReadIt() throws Exception {
        String adminRegisterPayload = """
                {
                  "username": "content-admin",
                  "email": "content-admin@example.com",
                  "password": "Password123!",
                  "firstName": "Content",
                  "lastName": "Admin"
                }
                """;

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(adminRegisterPayload))
                .andExpect(status().isCreated());

        String adminVerificationToken = extractTokenFromLatestEmail("content-admin@example.com");
        mockMvc.perform(get("/auth/verify-email")
                        .param("token", adminVerificationToken))
                .andExpect(status().isOk());

        UserAccount admin = userAccountRepository.findByUsernameIgnoreCaseOrEmailIgnoreCase("content-admin", "content-admin")
                .orElseThrow();
        admin.setRole(UserRole.ADMIN);
        userAccountRepository.save(admin);

        MvcResult adminLoginResult = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "usernameOrEmail": "content-admin",
                                  "password": "Password123!"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.role").value("ADMIN"))
                .andReturn();

        String adminToken = extractToken(adminLoginResult);

        MvcResult createCourseResult = mockMvc.perform(post("/courses")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "shortName": "CS300",
                                  "fullName": "Course Content Model",
                                  "categoryId": 12,
                                  "summary": "Course used for content model tests",
                                  "visible": true
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.shortName").value("CS300"))
                .andReturn();

        Long courseId = objectMapper.readTree(createCourseResult.getResponse().getContentAsString()).get("id").asLong();

        String teacherRegisterPayload = """
                {
                  "username": "content-teacher",
                  "email": "content-teacher@example.com",
                  "password": "Password123!",
                  "firstName": "Content",
                  "lastName": "Teacher"
                }
                """;

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(teacherRegisterPayload))
                .andExpect(status().isCreated());

        String teacherVerificationToken = extractTokenFromLatestEmail("content-teacher@example.com");
        mockMvc.perform(get("/auth/verify-email")
                        .param("token", teacherVerificationToken))
                .andExpect(status().isOk());

        UserAccount teacher = userAccountRepository.findByUsernameIgnoreCaseOrEmailIgnoreCase("content-teacher", "content-teacher")
                .orElseThrow();
        teacher.setRole(UserRole.ADMIN);
        userAccountRepository.save(teacher);

        mockMvc.perform(post("/courses/" + courseId + "/teachers/" + teacher.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());

        MvcResult teacherLoginResult = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "usernameOrEmail": "content-teacher",
                                  "password": "Password123!"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.role").value("ADMIN"))
                .andReturn();

        String teacherToken = extractToken(teacherLoginResult);

        MvcResult createSectionResult = mockMvc.perform(post("/courses/" + courseId + "/sections")
                        .header("Authorization", "Bearer " + teacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Week 1",
                                  "description": "Introduction",
                                  "orderIndex": 1,
                                  "visible": true,
                                  "locked": false
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Week 1"))
                .andReturn();

        Long sectionId = objectMapper.readTree(createSectionResult.getResponse().getContentAsString()).get("id").asLong();

        MvcResult createItemResult = mockMvc.perform(post("/courses/" + courseId + "/sections/" + sectionId + "/items")
                        .header("Authorization", "Bearer " + teacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "type": "TEXT",
                                  "title": "Welcome",
                                  "description": "Course introduction",
                                  "orderIndex": 1,
                                  "required": true,
                                  "visible": true,
                                  "payloadJson": "{\\"richText\\":true}",
                                  "published": true
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Welcome"))
                .andExpect(jsonPath("$.type").value("TEXT"))
                .andReturn();

        Long itemId = objectMapper.readTree(createItemResult.getResponse().getContentAsString()).get("id").asLong();

        MvcResult secondSectionResult = mockMvc.perform(post("/courses/" + courseId + "/sections")
                        .header("Authorization", "Bearer " + teacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Week 2",
                                  "description": "Follow-up",
                                  "orderIndex": 2,
                                  "visible": true,
                                  "locked": false
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Week 2"))
                .andReturn();

        Long secondSectionId = objectMapper.readTree(secondSectionResult.getResponse().getContentAsString()).get("id").asLong();

        MvcResult secondItemResult = mockMvc.perform(post("/courses/" + courseId + "/sections/" + sectionId + "/items")
                        .header("Authorization", "Bearer " + teacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "type": "TEXT",
                                  "title": "Recap",
                                  "description": "Second item",
                                  "orderIndex": 2,
                                  "required": false,
                                  "visible": true,
                                  "payloadJson": "{\\"notes\\":true}",
                                  "published": false
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Recap"))
                .andReturn();

        Long secondItemId = objectMapper.readTree(secondItemResult.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(patch("/courses/" + courseId + "/sections/" + secondSectionId + "/reorder")
                        .header("Authorization", "Bearer " + teacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "orderIndex": 1
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderIndex").value(1));

        mockMvc.perform(patch("/courses/" + courseId + "/sections/" + sectionId + "/items/" + secondItemId + "/reorder")
                        .header("Authorization", "Bearer " + teacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "orderIndex": 1
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderIndex").value(1));

        mockMvc.perform(post("/courses/" + courseId + "/sections/" + sectionId + "/items")
                        .header("Authorization", "Bearer " + teacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "type": "VIDEO",
                                  "title": "Broken video"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("VIDEO content requires sourceUrl")));

        mockMvc.perform(post("/courses/" + courseId + "/sections/" + sectionId + "/items")
                        .header("Authorization", "Bearer " + teacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "type": "PDF",
                                  "title": "Broken pdf"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("PDF content requires sourceUrl")));

        MvcResult quizItemResult = mockMvc.perform(post("/courses/" + courseId + "/sections/" + sectionId + "/items")
                        .header("Authorization", "Bearer " + teacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "type": "QUIZ",
                                  "title": "Quiz without questions"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.required").value(true))
                .andReturn();

        long quizItemId = objectMapper.readTree(quizItemResult.getResponse().getContentAsString()).get("id").asLong();
        mockMvc.perform(put("/courses/" + courseId + "/sections/" + sectionId + "/items/" + quizItemId + "/quiz")
                        .header("Authorization", "Bearer " + teacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "questions": []
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("at least one question")));

        mockMvc.perform(delete("/courses/" + courseId + "/sections/" + sectionId + "/items/" + quizItemId)
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/courses/" + courseId + "/details")
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(courseId.intValue()))
                .andExpect(jsonPath("$.teacherCount").doesNotExist())
                .andExpect(jsonPath("$.teachers").isArray())
                .andExpect(jsonPath("$.sectionCount").value(2))
                .andExpect(jsonPath("$.itemCount").value(2))
                .andExpect(jsonPath("$.sections[0].title").value("Week 2"))
                .andExpect(jsonPath("$.sections[0].orderIndex").value(1))
                .andExpect(jsonPath("$.sections[1].title").value("Week 1"))
                .andExpect(jsonPath("$.sections[1].orderIndex").value(2))
                .andExpect(jsonPath("$.sections[1].items[0].title").value("Recap"))
                .andExpect(jsonPath("$.sections[1].items[0].orderIndex").value(1))
                .andExpect(jsonPath("$.sections[1].items[1].title").value("Welcome"))
                .andExpect(jsonPath("$.sections[1].items[1].orderIndex").value(2));

        mockMvc.perform(get("/courses/" + courseId + "/content")
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("Week 2"))
                .andExpect(jsonPath("$[0].orderIndex").value(1))
                .andExpect(jsonPath("$[1].title").value("Week 1"))
                .andExpect(jsonPath("$[1].items[0].title").value("Recap"))
                .andExpect(jsonPath("$[1].items[1].title").value("Welcome"));

        mockMvc.perform(patch("/courses/" + courseId + "/sections/" + sectionId)
                        .header("Authorization", "Bearer " + teacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Week 1 Updated",
                                  "visible": false
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Week 1 Updated"))
                .andExpect(jsonPath("$.visible").value(false));

        mockMvc.perform(patch("/courses/" + courseId + "/sections/" + sectionId + "/items/" + itemId)
                        .header("Authorization", "Bearer " + teacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Welcome Updated",
                                  "required": false
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Welcome Updated"))
                .andExpect(jsonPath("$.required").value(false));

        String studentRegisterPayload = """
                {
                  "username": "content-student",
                  "email": "content-student@example.com",
                  "password": "Password123!",
                  "firstName": "Content",
                  "lastName": "Student"
                }
                """;

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(studentRegisterPayload))
                .andExpect(status().isCreated());

        String studentVerificationToken = extractTokenFromLatestEmail("content-student@example.com");
        mockMvc.perform(get("/auth/verify-email")
                        .param("token", studentVerificationToken))
                .andExpect(status().isOk());

        UserAccount student = userAccountRepository.findByUsernameIgnoreCaseOrEmailIgnoreCase("content-student", "content-student")
                .orElseThrow();

        MvcResult studentLoginResult = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "usernameOrEmail": "content-student",
                                  "password": "Password123!"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.role").value("STUDENT"))
                .andReturn();

        String studentToken = extractToken(studentLoginResult);

        mockMvc.perform(post("/courses/" + courseId + "/students/" + student.getId())
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/courses/" + courseId + "/details")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sections[1].items[1].title").value("Welcome Updated"));

        mockMvc.perform(get("/courses/" + courseId + "/content")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[1].items[1].title").value("Welcome Updated"));

        mockMvc.perform(delete("/courses/" + courseId + "/sections/" + sectionId + "/items/" + itemId)
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(delete("/courses/" + courseId + "/sections/" + sectionId)
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isNoContent());
    }

    @Test
    void adminCanCreateTemplateImportCourseAndDuplicateCourse() throws Exception {
        String adminRegisterPayload = """
                {
                  "username": "template-admin",
                  "email": "template-admin@example.com",
                  "password": "Password123!",
                  "firstName": "Template",
                  "lastName": "Admin"
                }
                """;

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(adminRegisterPayload))
                .andExpect(status().isCreated());

        String verificationToken = extractTokenFromLatestEmail("template-admin@example.com");
        mockMvc.perform(get("/auth/verify-email")
                        .param("token", verificationToken))
                .andExpect(status().isOk());

        UserAccount admin = userAccountRepository.findByUsernameIgnoreCaseOrEmailIgnoreCase("template-admin", "template-admin")
                .orElseThrow();
        admin.setRole(UserRole.ADMIN);
        userAccountRepository.save(admin);

        MvcResult loginResult = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "usernameOrEmail": "template-admin",
                                  "password": "Password123!"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.role").value("ADMIN"))
                .andReturn();

        String adminToken = extractToken(loginResult);

        MvcResult sourceCourseResult = mockMvc.perform(post("/courses")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "shortName": "CS400",
                                  "fullName": "Template Source Course",
                                  "categoryId": 12,
                                  "summary": "Source course for template tests",
                                  "visible": true
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn();

        Long sourceCourseId = objectMapper.readTree(sourceCourseResult.getResponse().getContentAsString()).get("id").asLong();

        MvcResult sourceSectionResult = mockMvc.perform(post("/courses/" + sourceCourseId + "/sections")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Template Week 1",
                                  "description": "Snapshot section",
                                  "orderIndex": 1,
                                  "visible": true,
                                  "locked": false
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn();

        Long sourceSectionId = objectMapper.readTree(sourceSectionResult.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(post("/courses/" + sourceCourseId + "/sections/" + sourceSectionId + "/items")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "type": "TEXT",
                                  "title": "Template Intro",
                                  "description": "Intro content",
                                  "orderIndex": 1,
                                  "required": true,
                                  "visible": true,
                                  "payloadJson": "{\\"intro\\":true}",
                                  "published": true
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Template Intro"));

        MvcResult templateResult = mockMvc.perform(post("/course-templates")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code": "TPL-001",
                                  "name": "Standard LMS Template",
                                  "description": "Standard course blueprint",
                                  "templateType": "STANDARD",
                                  "sourceCourseId": %d,
                                  "categoryIdDefault": 12,
                                  "visibleDefault": true,
                                  "active": true
                                }
                                """.formatted(sourceCourseId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("TPL-001"))
                .andExpect(jsonPath("$.templateType").value("STANDARD"))
                .andReturn();

        Long templateId = objectMapper.readTree(templateResult.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(get("/course-templates")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").value("TPL-001"));

        mockMvc.perform(get("/course-templates/" + templateId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(templateId.intValue()))
                .andExpect(jsonPath("$.name").value("Standard LMS Template"));

        mockMvc.perform(patch("/course-templates/" + templateId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Standard LMS Template v2",
                                  "active": true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Standard LMS Template v2"));

        MvcResult importedCourseResult = mockMvc.perform(post("/courses/import-template/" + templateId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "shortName": "CS401",
                                  "fullName": "Imported From Template",
                                  "categoryId": 12,
                                  "summary": "Created from template",
                                  "visible": false
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.shortName").value("CS401"))
                .andReturn();

        Long importedCourseId = objectMapper.readTree(importedCourseResult.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(get("/courses/" + importedCourseId + "/details")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sectionCount").value(1))
                .andExpect(jsonPath("$.itemCount").value(1))
                .andExpect(jsonPath("$.sections[0].title").value("Template Week 1"))
                .andExpect(jsonPath("$.sections[0].items[0].title").value("Template Intro"));

        MvcResult duplicatedCourseResult = mockMvc.perform(post("/courses/" + sourceCourseId + "/duplicate")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "shortName": "CS402",
                                  "fullName": "Duplicated Course",
                                  "categoryId": 12,
                                  "summary": "Duplicated from source",
                                  "visible": true,
                                  "copyTeachers": false,
                                  "copyStudents": false
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.shortName").value("CS402"))
                .andReturn();

        Long duplicatedCourseId = objectMapper.readTree(duplicatedCourseResult.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(get("/courses/" + duplicatedCourseId + "/content")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("Template Week 1"))
                .andExpect(jsonPath("$[0].items[0].title").value("Template Intro"));

        mockMvc.perform(delete("/course-templates/" + templateId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/course-templates/" + templateId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void adminCanManageCategories() throws Exception {
        String registerPayload = """
                {
                  "username": "gina",
                  "email": "gina@example.com",
                  "password": "Password123!",
                  "firstName": "Gina",
                  "lastName": "Lopez"
                }
                """;

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerPayload))
                .andExpect(status().isCreated());

        String verificationToken = extractTokenFromLatestEmail("gina@example.com");
        mockMvc.perform(get("/auth/verify-email")
                        .param("token", verificationToken))
                .andExpect(status().isOk());

        UserAccount gina = userAccountRepository.findByUsernameIgnoreCaseOrEmailIgnoreCase("gina", "gina")
                .orElseThrow();
        gina.setRole(UserRole.ADMIN);
        userAccountRepository.save(gina);

        MvcResult loginResult = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "usernameOrEmail": "gina",
                                  "password": "Password123!"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.role").value("ADMIN"))
                .andReturn();

        String loginToken = extractToken(loginResult);

        MvcResult createResult = mockMvc.perform(post("/categories")
                        .header("Authorization", "Bearer " + loginToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Science",
                                  "parent": 0,
                                  "idNumber": "SCI-001",
                                  "description": "Science category",
                                  "visible": true,
                                  "theme": "boost"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.name").value("Science"))
                .andExpect(jsonPath("$.idNumber").value("SCI-001"))
                .andExpect(jsonPath("$.description").value("Science category"))
                .andExpect(jsonPath("$.visible").value(true))
                .andExpect(jsonPath("$.theme").value("boost"))
                .andReturn();

        Long categoryId = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .get("id")
                .asLong();

        mockMvc.perform(get("/categories")
                        .header("Authorization", "Bearer " + loginToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].id").isNumber());

        mockMvc.perform(get("/categories/" + categoryId)
                        .header("Authorization", "Bearer " + loginToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(categoryId.intValue()))
                .andExpect(jsonPath("$.name").value("Science"));

        mockMvc.perform(patch("/categories/" + categoryId)
                        .header("Authorization", "Bearer " + loginToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Applied Science",
                                  "description": "Updated category"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(categoryId.intValue()))
                .andExpect(jsonPath("$.name").value("Applied Science"))
                .andExpect(jsonPath("$.description").value("Updated category"));

        mockMvc.perform(delete("/categories/" + categoryId)
                        .header("Authorization", "Bearer " + loginToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/categories/" + categoryId)
                        .header("Authorization", "Bearer " + loginToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void studentCanCompleteAndUpdateOwnProfile() throws Exception {
        MvcResult registerResult = mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "profile-student",
                                  "password": "Password123!",
                                  "website": "",
                                  "formStartedAt": %d
                                }
                                """.formatted(System.currentTimeMillis() - 5_000)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.firstLogin").value(true))
                .andExpect(jsonPath("$.completedProfile").value(false))
                .andReturn();

        String generatedEmail = objectMapper.readTree(registerResult.getResponse().getContentAsString())
                .get("email")
                .asText();

        MvcResult loginResult = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "usernameOrEmail": "profile-student",
                                  "password": "Password123!"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.email").value(generatedEmail))
                .andExpect(jsonPath("$.user.firstLogin").value(true))
                .andExpect(jsonPath("$.user.completedProfile").value(false))
                .andReturn();

        String accessToken = extractToken(loginResult);
        mockMvc.perform(put("/users/me")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "profile.student@example.com",
                                  "firstName": "Profile",
                                  "lastName": "Student"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("profile.student@example.com"))
                .andExpect(jsonPath("$.firstName").value("Profile"))
                .andExpect(jsonPath("$.lastName").value("Student"))
                .andExpect(jsonPath("$.firstLogin").value(false))
                .andExpect(jsonPath("$.completedProfile").value(true));

        mockMvc.perform(get("/users/me")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("profile.student@example.com"))
                .andExpect(jsonPath("$.firstLogin").value(false))
                .andExpect(jsonPath("$.completedProfile").value(true));
    }

    @Test
    void adminCanBulkEnrollStudentsFromExcelUsernames() throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "bulk-admin-001",
                                  "password": "Password123!",
                                  "website": "",
                                  "formStartedAt": %d
                                }
                                """.formatted(System.currentTimeMillis() - 5_000)))
                .andExpect(status().isCreated());

        UserAccount admin = userAccountRepository
                .findByUsernameIgnoreCaseOrEmailIgnoreCase("bulk-admin-001", "bulk-admin-001")
                .orElseThrow();
        admin.setRole(UserRole.ADMIN);
        userAccountRepository.save(admin);

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "bulk-student-001",
                                  "password": "Password123!",
                                  "website": "",
                                  "formStartedAt": %d
                                }
                                """.formatted(System.currentTimeMillis() - 5_000)))
                .andExpect(status().isCreated());

        MvcResult loginResult = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "usernameOrEmail": "bulk-admin-001",
                                  "password": "Password123!"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();
        String accessToken = extractToken(loginResult);

        MvcResult courseResult = mockMvc.perform(post("/courses")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "shortName": "BULK-001",
                                  "fullName": "Bulk enrollment test",
                                  "categoryId": 12,
                                  "visible": true
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        long courseId = objectMapper.readTree(courseResult.getResponse().getContentAsString()).get("id").asLong();

        byte[] excelBytes;
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("students");
            sheet.createRow(0).createCell(0).setCellValue("username");
            sheet.createRow(1).createCell(0).setCellValue("bulk-student-001");
            sheet.createRow(2).createCell(0).setCellValue("missing-student-001");
            sheet.createRow(3).createCell(0).setCellValue("bulk-admin-001");
            sheet.createRow(4).createCell(0).setCellValue("bulk-student-001");
            workbook.write(output);
            excelBytes = output.toByteArray();
        }

        org.springframework.mock.web.MockMultipartFile file = new org.springframework.mock.web.MockMultipartFile(
                "file",
                "students.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                excelBytes
        );

        mockMvc.perform(multipart("/courses/{id}/students/bulk-upload", courseId)
                        .file(file)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRows").value(4))
                .andExpect(jsonPath("$.enrolledCount").value(1))
                .andExpect(jsonPath("$.alreadyEnrolledCount").value(1))
                .andExpect(jsonPath("$.failedCount").value(2))
                .andExpect(jsonPath("$.results[0].status").value("ENROLLED"))
                .andExpect(jsonPath("$.results[1].status").value("FAILED"))
                .andExpect(jsonPath("$.results[2].status").value("FAILED"))
                .andExpect(jsonPath("$.results[3].status").value("ALREADY_ENROLLED"));
    }

    @Test
    void adminCanBulkCreateStudentsFromExcelCredentials() throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "bulk-create-admin-001",
                                  "password": "Password123!",
                                  "website": "",
                                  "formStartedAt": %d
                                }
                                """.formatted(System.currentTimeMillis() - 5_000)))
                .andExpect(status().isCreated());

        UserAccount admin = userAccountRepository
                .findByUsernameIgnoreCaseOrEmailIgnoreCase("bulk-create-admin-001", "bulk-create-admin-001")
                .orElseThrow();
        admin.setRole(UserRole.ADMIN);
        userAccountRepository.save(admin);

        MvcResult loginResult = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "usernameOrEmail": "bulk-create-admin-001",
                                  "password": "Password123!"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();
        String accessToken = extractToken(loginResult);

        byte[] excelBytes;
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("students");
            var header = sheet.createRow(0);
            header.createCell(0).setCellValue("username");
            header.createCell(1).setCellValue("password");

            var validRow = sheet.createRow(1);
            validRow.createCell(0).setCellValue("excel-student-001");
            validRow.createCell(1).setCellValue("Password123!");

            var duplicateRow = sheet.createRow(2);
            duplicateRow.createCell(0).setCellValue("excel-student-001");
            duplicateRow.createCell(1).setCellValue("Password123!");

            var weakPasswordRow = sheet.createRow(3);
            weakPasswordRow.createCell(0).setCellValue("excel-student-002");
            weakPasswordRow.createCell(1).setCellValue("weak");

            workbook.write(output);
            excelBytes = output.toByteArray();
        }

        org.springframework.mock.web.MockMultipartFile file = new org.springframework.mock.web.MockMultipartFile(
                "file",
                "student-credentials.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                excelBytes
        );

        mockMvc.perform(multipart("/v1/students/bulk-upload")
                        .file(file)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRows").value(3))
                .andExpect(jsonPath("$.createdCount").value(1))
                .andExpect(jsonPath("$.failedCount").value(2))
                .andExpect(jsonPath("$.results[0].username").value("excel-student-001"))
                .andExpect(jsonPath("$.results[0].status").value("CREATED"))
                .andExpect(jsonPath("$.results[1].status").value("FAILED"))
                .andExpect(jsonPath("$.results[2].status").value("FAILED"))
                .andExpect(jsonPath("$.results[0].password").doesNotExist());

        UserAccount student = userAccountRepository
                .findByUsernameIgnoreCaseOrEmailIgnoreCase("excel-student-001", "excel-student-001")
                .orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals(UserRole.STUDENT, student.getRole());
        org.junit.jupiter.api.Assertions.assertTrue(student.isFirstLogin());
        org.junit.jupiter.api.Assertions.assertFalse(student.isCompletedProfile());
    }

    private String extractToken(MvcResult result) throws Exception {
        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        return json.get("accessToken").asText();
    }

    private String extractTokenFromLatestEmail(String email) {
        ArgumentCaptor<String> htmlCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailService, atLeastOnce()).sendHtmlEmail(eq(email), anyString(), anyString(), htmlCaptor.capture());
        String htmlBody = htmlCaptor.getAllValues().get(htmlCaptor.getAllValues().size() - 1);
        Matcher matcher = Pattern.compile("token=([A-Za-z0-9_-]+)").matcher(htmlBody);
        if (!matcher.find()) {
            throw new IllegalStateException("Token not found in email body");
        }
        return matcher.group(1);
    }
}
