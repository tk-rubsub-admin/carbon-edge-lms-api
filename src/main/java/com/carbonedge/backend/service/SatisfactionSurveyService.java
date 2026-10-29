package com.carbonedge.backend.service;

import com.carbonedge.backend.dto.SatisfactionQuestionRequest;
import com.carbonedge.backend.dto.SatisfactionSubmissionRequest;
import com.carbonedge.backend.dto.SatisfactionSurveyResponse;
import com.carbonedge.backend.dto.SatisfactionSurveySaveRequest;
import com.carbonedge.backend.dto.SatisfactionDefaultQuestionsRequest;
import com.carbonedge.backend.dto.SatisfactionCourseSubmissionRequest;
import com.carbonedge.backend.exception.ConflictException;
import com.carbonedge.backend.exception.ForbiddenException;
import com.carbonedge.backend.exception.NotFoundException;
import com.carbonedge.backend.model.*;
import com.carbonedge.backend.repository.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SatisfactionSurveyService {
    private final AuthService authService;
    private final UserAccountRepository userRepository;
    private final CourseStudentEnrollmentRepository enrollmentRepository;
    private final SatisfactionSurveyRepository surveyRepository;
    private final SatisfactionQuestionRepository questionRepository;
    private final SatisfactionResponseRepository responseRepository;
    private final SatisfactionAnswerRepository answerRepository;
    private final SatisfactionDefaultQuestionRepository defaultQuestionRepository;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public List<SatisfactionSurveyResponse> listAdmin(String authorization) {
        requireAdmin(current(authorization));
        return surveyRepository.findAllByOrderByUpdatedAtDesc().stream()
                .map(survey -> response(survey, questionRepository.findAllBySurveyIdAndActiveTrueOrderByOrderIndexAscIdAsc(survey.getId()),
                        responseRepository.countBySurveyId(survey.getId()), false))
                .toList();
    }

    @Transactional(readOnly = true)
    public SatisfactionSurveyResponse getAdmin(String authorization, Long surveyId) {
        requireAdmin(current(authorization));
        SatisfactionSurvey survey = loadSurvey(surveyId);
        return response(survey, questionRepository.findAllBySurveyIdAndActiveTrueOrderByOrderIndexAscIdAsc(surveyId),
                responseRepository.countBySurveyId(surveyId), false);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getDefaultQuestions(String authorization) {
        requireAdmin(current(authorization));
        return defaultQuestionRepository.findAllByOrderByOrderIndexAsc().stream().map(question -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("orderIndex", question.getOrderIndex());
            row.put("prompt", question.getPrompt());
            return row;
        }).toList();
    }

    @Transactional
    public List<Map<String, Object>> saveDefaultQuestions(String authorization, SatisfactionDefaultQuestionsRequest request) {
        requireAdmin(current(authorization));
        if (request == null || request.prompts() == null || request.prompts().size() != 3) {
            throw new IllegalArgumentException("Exactly three default questions are required");
        }
        List<String> prompts = request.prompts().stream().map(this::trim).toList();
        if (prompts.stream().anyMatch(prompt -> prompt == null || prompt.length() > 2000)) {
            throw new IllegalArgumentException("Both question prompts are required and must be at most 2000 characters");
        }
        List<SatisfactionDefaultQuestion> defaults = defaultQuestionRepository.findAllByOrderByOrderIndexAsc();
        if (defaults.size() != 3) throw new IllegalStateException("Default satisfaction questions are not configured");
        for (int i = 0; i < 3; i++) {
            defaults.get(i).setPrompt(prompts.get(i));
            defaultQuestionRepository.save(defaults.get(i));
        }
        for (SatisfactionSurvey survey : surveyRepository.findAllByDefaultForCourseIdIsNotNull()) {
            List<SatisfactionQuestion> questions = questionRepository.findAllBySurveyIdOrderByOrderIndexAscIdAsc(survey.getId());
            for (int i = 0; i < 3; i++) {
                int order = i + 1;
                SatisfactionQuestion question = questions.stream().filter(q -> Objects.equals(q.getOrderIndex(), order)).findFirst()
                        .orElseGet(() -> { SatisfactionQuestion created = new SatisfactionQuestion(); created.setSurvey(survey); return created; });
                question.setType(order == 3 ? SatisfactionQuestionType.TEXT : SatisfactionQuestionType.RATING);
                question.setPrompt(prompts.get(i));
                question.setOptionsJson(null);
                question.setRequired(order != 3);
                question.setOrderIndex(i + 1);
                question.setActive(true);
                questionRepository.save(question);
            }
            questions.stream().filter(question -> question.getOrderIndex() == null || question.getOrderIndex() > 3)
                    .filter(SatisfactionQuestion::isActive).forEach(question -> { question.setActive(false); questionRepository.save(question); });
        }
        return getDefaultQuestions(authorization);
    }

    @Transactional
    public SatisfactionSurveyResponse save(String authorization, Long surveyId, SatisfactionSurveySaveRequest request) {
        requireAdmin(current(authorization));
        validateSurvey(request);
        SatisfactionSurvey survey = surveyId == null ? new SatisfactionSurvey() : loadSurvey(surveyId);
        if (surveyId != null && survey.getDefaultForCourseId() != null) {
            throw new ConflictException("Default course satisfaction surveys cannot be edited here");
        }
        if (surveyId != null && survey.getStatus() == SatisfactionSurveyStatus.ARCHIVED) {
            throw new ConflictException("Archived surveys cannot be edited");
        }
        survey.setCourseId(request.courseId());
        survey.setTitle(request.title().trim());
        survey.setDescription(trim(request.description()));
        survey.setStatus(request.status() == null ? SatisfactionSurveyStatus.DRAFT : request.status());
        survey.setOpensAt(request.opensAt());
        survey.setClosesAt(request.closesAt());
        survey = surveyRepository.save(survey);

        List<SatisfactionQuestion> existing = questionRepository.findAllBySurveyIdOrderByOrderIndexAscIdAsc(survey.getId());
        Map<Long, SatisfactionQuestion> byId = existing.stream().collect(Collectors.toMap(SatisfactionQuestion::getId, value -> value));
        Set<Long> retainedIds = new HashSet<>();
        List<SatisfactionQuestionRequest> inputs = request.questions() == null ? List.of() : request.questions();
        for (int index = 0; index < inputs.size(); index++) {
            SatisfactionQuestionRequest input = inputs.get(index);
            validateQuestion(input);
            SatisfactionQuestion question;
            if (input.id() == null) {
                question = new SatisfactionQuestion();
                question.setSurvey(survey);
            } else {
                question = byId.get(input.id());
                if (question == null || !retainedIds.add(input.id())) {
                    throw new IllegalArgumentException("Question does not belong to this survey or is duplicated");
                }
            }
            question.setType(input.type());
            question.setPrompt(input.prompt().trim());
            question.setOptionsJson(encodeOptions(input.options()));
            question.setRequired(!Boolean.FALSE.equals(input.required()));
            question.setOrderIndex(index + 1);
            question.setActive(true);
            questionRepository.save(question);
        }
        for (SatisfactionQuestion old : existing) {
            if (!retainedIds.contains(old.getId()) && old.isActive()) {
                old.setActive(false);
                questionRepository.save(old);
            }
        }
        if (survey.getStatus() == SatisfactionSurveyStatus.PUBLISHED
                && questionRepository.countBySurveyIdAndActiveTrue(survey.getId()) == 0) {
            throw new IllegalArgumentException("Published surveys must contain at least one question");
        }
        return response(survey, questionRepository.findAllBySurveyIdAndActiveTrueOrderByOrderIndexAscIdAsc(survey.getId()),
                responseRepository.countBySurveyId(survey.getId()), false);
    }

    @Transactional
    public void delete(String authorization, Long surveyId) {
        requireAdmin(current(authorization));
        SatisfactionSurvey survey = loadSurvey(surveyId);
        if (survey.getDefaultForCourseId() != null) {
            throw new ConflictException("Default course satisfaction surveys cannot be deleted");
        }
        if (responseRepository.countBySurveyId(surveyId) > 0) {
            survey.setStatus(SatisfactionSurveyStatus.ARCHIVED);
            surveyRepository.save(survey);
            return;
        }
        questionRepository.deleteAllBySurveyId(surveyId);
        surveyRepository.delete(survey);
    }

    @Transactional
    public List<SatisfactionSurveyResponse> available(String authorization) {
        UserAccount student = current(authorization);
        requireStudent(student);
        Set<Long> courseIds = enrollmentRepository.findAllByUserId(student.getId()).stream()
                .map(CourseStudentEnrollment::getCourseId).collect(Collectors.toSet());
        courseIds.forEach(this::ensureDefaultSurvey);
        Instant now = Instant.now();
        return surveyRepository.findAllByStatusOrderByOpensAtAsc(SatisfactionSurveyStatus.PUBLISHED).stream()
                .filter(survey -> courseIds.contains(survey.getCourseId()))
                .filter(survey -> survey.getOpensAt() == null || !now.isBefore(survey.getOpensAt()))
                .filter(survey -> survey.getClosesAt() == null || !now.isAfter(survey.getClosesAt()))
                .map(survey -> response(survey, List.of(), 0, responseRepository.existsBySurveyIdAndUserId(survey.getId(), student.getId())))
                .toList();
    }

    @Transactional
    public Map<String, Object> studentQuestionnaire(String authorization, Long courseId) {
        UserAccount student = current(authorization);
        requireStudent(student);
        if (!enrollmentRepository.existsByUserIdAndCourseId(student.getId(), courseId)) {
            throw new ForbiddenException("User is not enrolled in this course");
        }
        ensureDefaultSurvey(courseId);
        List<SatisfactionSurvey> surveys = availableCourseSurveys(courseId);
        List<Map<String, Object>> surveyRows = surveys.stream().map(survey -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", survey.getId());
            row.put("title", survey.getTitle());
            row.put("defaultSurvey", survey.getDefaultForCourseId() != null);
            row.put("submitted", responseRepository.existsBySurveyIdAndUserId(survey.getId(), student.getId()));
            return row;
        }).toList();
        List<Map<String, Object>> questions = new ArrayList<>();
        List<SatisfactionSurvey> defaults = surveys.stream().filter(survey -> survey.getDefaultForCourseId() != null).toList();
        List<SatisfactionSurvey> custom = surveys.stream().filter(survey -> survey.getDefaultForCourseId() == null).toList();
        defaults.forEach(survey -> appendQuestions(questions, survey, student, q -> Objects.equals(q.getOrderIndex(), 1) || Objects.equals(q.getOrderIndex(), 2)));
        custom.forEach(survey -> appendQuestions(questions, survey, student, q -> true));
        defaults.forEach(survey -> appendQuestions(questions, survey, student, q -> Objects.equals(q.getOrderIndex(), 3)));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("courseId", courseId);
        result.put("surveys", surveyRows);
        result.put("questions", questions);
        return result;
    }

    @Transactional
    public Map<String, Object> submitCourseQuestionnaire(String authorization, Long courseId,
            SatisfactionCourseSubmissionRequest request) {
        UserAccount student = current(authorization);
        requireStudent(student);
        if (!enrollmentRepository.existsByUserIdAndCourseId(student.getId(), courseId)) {
            throw new ForbiddenException("User is not enrolled in this course");
        }
        ensureDefaultSurvey(courseId);
        if (request == null || request.answers() == null) throw new IllegalArgumentException("Answers are required");
        List<SatisfactionSurvey> surveys = availableCourseSurveys(courseId);
        Map<Long, SatisfactionSurvey> surveyById = surveys.stream().collect(Collectors.toMap(SatisfactionSurvey::getId, survey -> survey));
        Set<Long> answerSurveyIds = request.answers().stream().map(SatisfactionCourseSubmissionRequest.Answer::surveyId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, SatisfactionSurvey> pendingSurveys = surveyById.entrySet().stream()
                .filter(entry -> !responseRepository.existsBySurveyIdAndUserId(entry.getKey(), student.getId()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        if (!answerSurveyIds.equals(pendingSurveys.keySet())) throw new IllegalArgumentException("Submit all available course surveys together");

        Map<Long, Map<Long, String>> submittedBySurvey = new HashMap<>();
        for (SatisfactionCourseSubmissionRequest.Answer answer : request.answers()) {
            if (answer == null || answer.surveyId() == null || answer.questionId() == null || !pendingSurveys.containsKey(answer.surveyId())) {
                throw new IllegalArgumentException("Answer contains an invalid survey or question");
            }
            Map<Long, String> answers = submittedBySurvey.computeIfAbsent(answer.surveyId(), ignored -> new HashMap<>());
            if (answers.putIfAbsent(answer.questionId(), answer.answer()) != null) throw new IllegalArgumentException("Duplicate survey answer");
        }

        for (SatisfactionSurvey survey : pendingSurveys.values()) {
            List<SatisfactionQuestion> questions = questionRepository.findAllBySurveyIdAndActiveTrueOrderByOrderIndexAscIdAsc(survey.getId());
            Map<Long, String> submitted = submittedBySurvey.getOrDefault(survey.getId(), Map.of());
            Set<Long> validIds = questions.stream().map(SatisfactionQuestion::getId).collect(Collectors.toSet());
            if (!validIds.containsAll(submitted.keySet())) throw new IllegalArgumentException("Answer contains an invalid question");
            for (SatisfactionQuestion question : questions) {
                String value = trim(submitted.get(question.getId()));
                if (question.isRequired() && value == null) throw new IllegalArgumentException("Please answer all required questions");
                if (value != null) validateAnswer(question, value);
            }
        }

        for (SatisfactionSurvey survey : pendingSurveys.values()) {
            SatisfactionResponse response = new SatisfactionResponse();
            response.setSurvey(survey);
            response.setUser(student);
            response.setSurveyTitleSnapshot(survey.getTitle());
            response = responseRepository.save(response);
            Map<Long, String> submitted = submittedBySurvey.getOrDefault(survey.getId(), Map.of());
            for (SatisfactionQuestion question : questionRepository.findAllBySurveyIdAndActiveTrueOrderByOrderIndexAscIdAsc(survey.getId())) {
                String value = trim(submitted.get(question.getId()));
                if (value == null) continue;
                SatisfactionAnswer saved = new SatisfactionAnswer();
                saved.setResponse(response);
                saved.setQuestion(question);
                saved.setQuestionPromptSnapshot(question.getPrompt());
                saved.setQuestionTypeSnapshot(question.getType());
                saved.setAnswerText(value);
                answerRepository.save(saved);
            }
        }
        return Map.of("submitted", true, "surveyCount", pendingSurveys.size());
    }

    private List<SatisfactionSurvey> availableCourseSurveys(Long courseId) {
        Instant now = Instant.now();
        return surveyRepository.findAllByCourseId(courseId).stream()
                .filter(survey -> survey.getStatus() == SatisfactionSurveyStatus.PUBLISHED)
                .filter(survey -> survey.getOpensAt() == null || !now.isBefore(survey.getOpensAt()))
                .filter(survey -> survey.getClosesAt() == null || !now.isAfter(survey.getClosesAt()))
                .sorted(Comparator.comparing((SatisfactionSurvey survey) -> survey.getDefaultForCourseId() == null)
                        .thenComparing(SatisfactionSurvey::getId))
                .toList();
    }

    private void appendQuestions(List<Map<String, Object>> result, SatisfactionSurvey survey, UserAccount student,
            java.util.function.Predicate<SatisfactionQuestion> filter) {
        if (responseRepository.existsBySurveyIdAndUserId(survey.getId(), student.getId())) return;
        questionRepository.findAllBySurveyIdAndActiveTrueOrderByOrderIndexAscIdAsc(survey.getId()).stream().filter(filter).forEach(question -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", question.getId());
            row.put("surveyId", survey.getId());
            row.put("surveyTitle", survey.getTitle());
            row.put("type", question.getType());
            row.put("prompt", question.getPrompt());
            row.put("options", decodeOptions(question.getOptionsJson()));
            row.put("required", question.isRequired());
            row.put("orderIndex", question.getOrderIndex());
            result.add(row);
        });
    }

    @Transactional
    public Map<String, Object> studentCourseStatus(String authorization, Long courseId) {
        UserAccount student = current(authorization);
        requireStudent(student);
        if (!enrollmentRepository.existsByUserIdAndCourseId(student.getId(), courseId)) {
            throw new ForbiddenException("User is not enrolled in this course");
        }
        ensureDefaultSurvey(courseId);
        Instant now = Instant.now();
        List<SatisfactionSurvey> relevantSurveys = surveyRepository.findAllByCourseId(courseId).stream()
                .filter(survey -> survey.getStatus() == SatisfactionSurveyStatus.PUBLISHED
                        || responseRepository.existsBySurveyIdAndUserId(survey.getId(), student.getId()))
                .toList();
        List<Map<String, Object>> surveys = relevantSurveys.stream().map(survey -> {
                    boolean submitted = responseRepository.existsBySurveyIdAndUserId(survey.getId(), student.getId());
                    boolean available = (survey.getOpensAt() == null || !now.isBefore(survey.getOpensAt()))
                            && (survey.getClosesAt() == null || !now.isAfter(survey.getClosesAt()));
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", survey.getId());
                    row.put("title", survey.getTitle());
                    row.put("submitted", submitted);
                    row.put("available", survey.getStatus() == SatisfactionSurveyStatus.PUBLISHED && available);
                    return row;
                }).toList();
        boolean hasSubmittedSurvey = surveys.stream().anyMatch(survey -> Boolean.TRUE.equals(survey.get("submitted")));
        boolean evaluationComplete = hasSubmittedSurvey && surveyRepository
                .findAllByCourseIdAndStatus(courseId, SatisfactionSurveyStatus.PUBLISHED).stream()
                .allMatch(survey -> responseRepository.existsBySurveyIdAndUserId(survey.getId(), student.getId()));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("courseId", courseId);
        result.put("surveys", surveys);
        result.put("evaluationComplete", evaluationComplete);
        return result;
    }

    @Transactional(readOnly = true)
    public SatisfactionSurveyResponse getForStudent(String authorization, Long surveyId) {
        UserAccount student = current(authorization);
        requireStudent(student);
        SatisfactionSurvey survey = loadAvailableSurvey(surveyId, student);
        return response(survey, questionRepository.findAllBySurveyIdAndActiveTrueOrderByOrderIndexAscIdAsc(surveyId),
                0, responseRepository.existsBySurveyIdAndUserId(surveyId, student.getId()));
    }

    @Transactional
    public Map<String, Object> submit(String authorization, Long surveyId, SatisfactionSubmissionRequest request) {
        UserAccount student = current(authorization);
        requireStudent(student);
        SatisfactionSurvey survey = loadAvailableSurvey(surveyId, student);
        if (responseRepository.existsBySurveyIdAndUserId(surveyId, student.getId())) {
            throw new ConflictException("You have already submitted this survey");
        }
        List<SatisfactionQuestion> questions = questionRepository.findAllBySurveyIdAndActiveTrueOrderByOrderIndexAscIdAsc(surveyId);
        Map<Long, String> submitted = new HashMap<>();
        if (request != null && request.answers() != null) {
            for (SatisfactionSubmissionRequest.Answer answer : request.answers()) {
                if (answer == null || answer.questionId() == null) throw new IllegalArgumentException("Question is required for each answer");
                if (submitted.containsKey(answer.questionId())) throw new IllegalArgumentException("Duplicate survey answer");
                submitted.put(answer.questionId(), answer.answer());
            }
        }
        Set<Long> validQuestionIds = questions.stream().map(SatisfactionQuestion::getId).collect(Collectors.toSet());
        if (!validQuestionIds.containsAll(submitted.keySet())) throw new IllegalArgumentException("Answer contains an invalid question");
        for (SatisfactionQuestion question : questions) {
            String answer = trim(submitted.get(question.getId()));
            if (question.isRequired() && answer == null) throw new IllegalArgumentException("Please answer all required questions");
            if (answer != null) validateAnswer(question, answer);
        }

        SatisfactionResponse response = new SatisfactionResponse();
        response.setSurvey(survey);
        response.setUser(student);
        response.setSurveyTitleSnapshot(survey.getTitle());
        response = responseRepository.save(response);
        for (SatisfactionQuestion question : questions) {
            String answerText = trim(submitted.get(question.getId()));
            if (answerText == null) continue;
            SatisfactionAnswer answer = new SatisfactionAnswer();
            answer.setResponse(response);
            answer.setQuestion(question);
            answer.setQuestionPromptSnapshot(question.getPrompt());
            answer.setQuestionTypeSnapshot(question.getType());
            answer.setAnswerText(answerText);
            answerRepository.save(answer);
        }
        return Map.of("submitted", true, "submittedAt", response.getSubmittedAt());
    }

    @Transactional(readOnly = true)
    public Map<String, Object> results(String authorization, Long surveyId) {
        requireAdmin(current(authorization));
        SatisfactionSurvey survey = loadSurvey(surveyId);
        List<SatisfactionAnswer> answers = answerRepository.findAllBySurveyId(surveyId);
        Map<String, List<SatisfactionAnswer>> grouped = answers.stream().collect(Collectors.groupingBy(
                answer -> answer.getQuestionPromptSnapshot() + "\u0000" + answer.getQuestionTypeSnapshot(),
                LinkedHashMap::new, Collectors.toList()));
        List<Map<String, Object>> questionResults = new ArrayList<>();
        for (Map.Entry<String, List<SatisfactionAnswer>> entry : grouped.entrySet()) {
            List<SatisfactionAnswer> values = entry.getValue();
            SatisfactionAnswer first = values.get(0);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("prompt", first.getQuestionPromptSnapshot());
            result.put("type", first.getQuestionTypeSnapshot());
            result.put("answerCount", values.size());
            if (first.getQuestionTypeSnapshot() == SatisfactionQuestionType.RATING) {
                List<Integer> ratings = values.stream().map(SatisfactionAnswer::getAnswerText).map(Integer::valueOf).toList();
                result.put("averageRating", ratings.stream().mapToInt(Integer::intValue).average().orElse(0));
                result.put("ratingCounts", ratings.stream().collect(Collectors.groupingBy(value -> value, TreeMap::new, Collectors.counting())));
            } else if (first.getQuestionTypeSnapshot() == SatisfactionQuestionType.SINGLE_CHOICE) {
                result.put("choiceCounts", values.stream().collect(Collectors.groupingBy(SatisfactionAnswer::getAnswerText, LinkedHashMap::new, Collectors.counting())));
            } else {
                result.put("comments", values.stream().map(SatisfactionAnswer::getAnswerText).toList());
            }
            questionResults.add(result);
        }
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("surveyId", survey.getId());
        response.put("title", survey.getTitle());
        response.put("responseCount", responseRepository.countBySurveyId(surveyId));
        response.put("questions", questionResults);
        return response;
    }

    @Transactional
    public Map<String, Object> courseSummary(String authorization, Long courseId) {
        UserAccount user = current(authorization);
        if (user.getRole() != UserRole.ADMIN && !(user.getRole() == UserRole.STUDENT
                && enrollmentRepository.existsByUserIdAndCourseId(user.getId(), courseId))) {
            throw new ForbiddenException("User cannot access this course satisfaction summary");
        }
        ensureDefaultSurvey(courseId);
        List<SatisfactionAnswer> ratings = answerRepository.findAllByCourseIdAndQuestionType(
                courseId, SatisfactionQuestionType.RATING);
        double average = ratings.stream().map(SatisfactionAnswer::getAnswerText).mapToInt(Integer::parseInt)
                .average().orElse(0);
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("courseId", courseId);
        summary.put("averageRating", ratings.isEmpty() ? null : average);
        summary.put("ratingCount", ratings.size());
        summary.put("responseCount", responseRepository.countByCourseId(courseId));
        summary.put("surveyCount", surveyRepository.findAllByCourseId(courseId).size());
        return summary;
    }

    @Transactional
    public void ensureDefaultSurvey(Long courseId) {
        if (courseId == null || courseId <= 0 || surveyRepository.findByDefaultForCourseId(courseId).isPresent()) return;

        SatisfactionSurvey survey = new SatisfactionSurvey();
        survey.setCourseId(courseId);
        survey.setDefaultForCourseId(courseId);
        survey.setTitle("แบบประเมินความพึงพอใจหลัก");
        survey.setDescription("แบบประเมินมาตรฐานสำหรับทุกคอร์ส");
        survey.setStatus(SatisfactionSurveyStatus.PUBLISHED);
        survey.setOpensAt(null);
        survey.setClosesAt(null);
        survey = surveyRepository.save(survey);
        SatisfactionSurvey savedSurvey = survey;

        List<SatisfactionDefaultQuestion> defaults = defaultQuestionRepository.findAllByOrderByOrderIndexAsc();
        if (defaults.size() != 3) throw new IllegalStateException("Default satisfaction questions are not configured");
        defaults.forEach(question -> saveDefaultQuestion(savedSurvey, question.getOrderIndex(), question.getPrompt()));
    }

    private void saveDefaultQuestion(SatisfactionSurvey survey, int order, String prompt) {
        SatisfactionQuestion question = new SatisfactionQuestion();
        question.setSurvey(survey);
        question.setType(order == 3 ? SatisfactionQuestionType.TEXT : SatisfactionQuestionType.RATING);
        question.setPrompt(prompt);
        question.setOptionsJson(null);
        question.setRequired(order != 3);
        question.setOrderIndex(order);
        question.setActive(true);
        questionRepository.save(question);
    }

    private SatisfactionSurvey loadAvailableSurvey(Long surveyId, UserAccount student) {
        SatisfactionSurvey survey = loadSurvey(surveyId);
        Instant now = Instant.now();
        if (survey.getStatus() != SatisfactionSurveyStatus.PUBLISHED
                || (survey.getOpensAt() != null && now.isBefore(survey.getOpensAt()))
                || (survey.getClosesAt() != null && now.isAfter(survey.getClosesAt()))
                || !enrollmentRepository.existsByUserIdAndCourseId(student.getId(), survey.getCourseId())) {
            throw new NotFoundException("Survey not available");
        }
        return survey;
    }

    private SatisfactionSurvey loadSurvey(Long id) {
        return surveyRepository.findById(id).orElseThrow(() -> new NotFoundException("Survey not found"));
    }

    private SatisfactionSurveyResponse response(SatisfactionSurvey survey, List<SatisfactionQuestion> questions,
            long count, boolean submitted) {
        return new SatisfactionSurveyResponse(survey.getId(), survey.getCourseId(), survey.getTitle(),
                survey.getDescription(), survey.getStatus(), survey.getOpensAt(), survey.getClosesAt(), count,
                questions.stream().map(question -> new SatisfactionSurveyResponse.Question(question.getId(),
                        question.getType(), question.getPrompt(), decodeOptions(question.getOptionsJson()),
                        question.isRequired(), question.getOrderIndex())).toList(), submitted,
                survey.getDefaultForCourseId() != null);
    }

    private void validateSurvey(SatisfactionSurveySaveRequest request) {
        if (request == null || request.courseId() == null || request.courseId() <= 0 || trim(request.title()) == null) {
            throw new IllegalArgumentException("Course and survey title are required");
        }
        if (request.title().trim().length() > 200) throw new IllegalArgumentException("Survey title is too long");
        if (request.description() != null && request.description().length() > 4000) throw new IllegalArgumentException("Description is too long");
        if (request.opensAt() != null && request.closesAt() != null && !request.opensAt().isBefore(request.closesAt())) {
            throw new IllegalArgumentException("Closing time must be after opening time");
        }
        if (request.status() == SatisfactionSurveyStatus.ARCHIVED) throw new IllegalArgumentException("Use delete to archive a survey");
    }

    private void validateQuestion(SatisfactionQuestionRequest question) {
        if (question == null || question.type() == null || trim(question.prompt()) == null || question.prompt().trim().length() > 2000) {
            throw new IllegalArgumentException("Each question must have a valid type and prompt");
        }
        if (question.type() == SatisfactionQuestionType.SINGLE_CHOICE) {
            List<String> options = question.options() == null ? List.of() : question.options().stream().map(this::trim).filter(Objects::nonNull).toList();
            if (options.size() < 2 || options.size() != new HashSet<>(options).size()
                    || options.stream().anyMatch(option -> option.length() > 500)) {
                throw new IllegalArgumentException("Choice questions require at least two valid options");
            }
        }
    }

    private void validateAnswer(SatisfactionQuestion question, String value) {
        if (question.getType() == SatisfactionQuestionType.RATING) {
            try {
                int rating = Integer.parseInt(value);
                if (rating < 1 || rating > 5) throw new IllegalArgumentException("Rating must be between 1 and 5");
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException("Rating must be between 1 and 5");
            }
        } else if (question.getType() == SatisfactionQuestionType.SINGLE_CHOICE
                && !decodeOptions(question.getOptionsJson()).contains(value)) {
            throw new IllegalArgumentException("Invalid choice selected");
        } else if (question.getType() == SatisfactionQuestionType.TEXT && value.length() > 4000) {
            throw new IllegalArgumentException("Comment is too long");
        }
    }

    private String encodeOptions(List<String> options) {
        if (options == null || options.isEmpty()) return null;
        List<String> normalized = options.stream().map(this::trim).filter(Objects::nonNull).toList();
        try { return objectMapper.writeValueAsString(normalized); }
        catch (Exception exception) { throw new IllegalArgumentException("Invalid question options"); }
    }

    private List<String> decodeOptions(String value) {
        if (value == null || value.isBlank()) return List.of();
        try { return objectMapper.readValue(value, new TypeReference<>() { }); }
        catch (Exception exception) { return List.of(); }
    }

    private UserAccount current(String authorization) {
        return userRepository.findById(authService.currentUser(authorization).id()).orElseThrow();
    }
    private void requireAdmin(UserAccount user) {
        if (user.getRole() != UserRole.ADMIN) throw new ForbiddenException("Admin access is required");
    }
    private void requireStudent(UserAccount user) {
        if (user.getRole() != UserRole.STUDENT) throw new ForbiddenException("Student access is required");
    }
    private String trim(String value) {
        if (value == null) return null;
        String result = value.trim();
        return result.isEmpty() ? null : result;
    }
}
