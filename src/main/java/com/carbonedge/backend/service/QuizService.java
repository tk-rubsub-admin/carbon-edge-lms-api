package com.carbonedge.backend.service;

import com.carbonedge.backend.dto.*;
import com.carbonedge.backend.exception.ForbiddenException;
import com.carbonedge.backend.exception.NotFoundException;
import com.carbonedge.backend.model.*;
import com.carbonedge.backend.repository.*;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class QuizService {
    private final AuthSessionRepository authSessionRepository;
    private final CourseContentItemRepository itemRepository;
    private final CourseStudentEnrollmentRepository enrollmentRepository;
    private final MoodleUserMappingRepository mappingRepository;
    private final QuizRepository quizRepository;
    private final QuizQuestionRepository questionRepository;
    private final QuizOptionRepository optionRepository;
    private final QuizAttemptRepository attemptRepository;
    private final QuizAttemptAnswerRepository answerRepository;
    private final MoodleLaunchService launchService;
    private final MoodleContentProgressService progressService;
    private final CourseContentAccessService courseContentAccessService;

    @Transactional(readOnly = true)
    public QuizDefinitionResponse getDefinition(String authorization, Long courseId, Long sectionId, Long itemId) {
        requireEditor(authorization);
        return definition(loadQuiz(courseId, sectionId, itemId), true);
    }

    @Transactional
    public QuizDefinitionResponse saveDefinition(String authorization, Long courseId, Long sectionId, Long itemId, QuizDefinitionRequest request) {
        requireEditor(authorization);
        CourseContentItem item = loadItem(courseId, sectionId, itemId);
        if (item.getType() != CourseContentType.QUIZ) throw new IllegalArgumentException("Content item is not a QUIZ");
        validateDefinition(request);
        Quiz quiz = quizRepository.findByContentItemId(itemId).orElseGet(() -> { Quiz value = new Quiz(); value.setContentItem(item); return value; });
        quiz.setPassingScore(request.passingScore() == null ? 0 : request.passingScore());
        quiz.setMaxAttempts(request.maxAttempts()); quiz.setTimeLimitSeconds(request.timeLimitSeconds());
        quiz.setOpensAt(request.opensAt()); quiz.setClosesAt(request.closesAt()); quiz.setPublished(Boolean.TRUE.equals(request.published()));
        quiz.setRandomizeQuestions(Boolean.TRUE.equals(request.randomizeQuestions()));
        quiz.setRandomizeOptions(Boolean.TRUE.equals(request.randomizeOptions()));
        quiz = quizRepository.save(quiz);
        answerRepository.deleteByAttemptQuizId(quiz.getId()); attemptRepository.deleteByQuizId(quiz.getId());
        List<QuizQuestion> old = questionRepository.findAllByQuizIdOrderByOrderIndexAscIdAsc(quiz.getId());
        optionRepository.deleteByQuestionIdIn(old.stream().map(QuizQuestion::getId).toList()); questionRepository.deleteByQuizId(quiz.getId());
        for (int index = 0; index < request.questions().size(); index++) {
            QuizQuestionRequest input = request.questions().get(index);
            QuizQuestion question = new QuizQuestion(); question.setQuiz(quiz); question.setType(input.type()); question.setPrompt(input.prompt().trim());
            question.setFeedback(trim(input.feedback())); question.setPoints(input.points()); question.setOrderIndex(index + 1); question = questionRepository.save(question);
            for (int optionIndex = 0; optionIndex < input.options().size(); optionIndex++) {
                QuizOptionRequest optionInput = input.options().get(optionIndex);
                QuizOption option = new QuizOption(); option.setQuestion(question); option.setText(optionInput.text().trim()); option.setCorrect(Boolean.TRUE.equals(optionInput.correct())); option.setOrderIndex(optionIndex + 1); optionRepository.save(option);
            }
        }
        return definition(quiz, true);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> learnerDefinition(
            String secret, Long courseId, Long itemId, Long moodleUserId, Long attemptId
    ) {
        UserAccount user = verifyLearner(secret, courseId, moodleUserId);
        Quiz quiz = quizRepository.findByContentItemId(itemId).orElseThrow(() -> new NotFoundException("Quiz not found"));
        if (!quiz.getContentItem().getSection().getCourseId().equals(courseId) || !quiz.isPublished()) throw new NotFoundException("Quiz not available");
        courseContentAccessService.requireUnlocked(user, courseId, itemId);
        validateAvailability(quiz);
        long randomSeed = 31L * user.getId() + quiz.getId();
        if (attemptId != null) {
            QuizAttempt attempt = attemptRepository.findById(attemptId)
                    .orElseThrow(() -> new NotFoundException("Quiz attempt not found"));
            if (!attempt.getQuiz().getId().equals(quiz.getId()) || !attempt.getUser().getId().equals(user.getId())) {
                throw new NotFoundException("Quiz attempt not found");
            }
            randomSeed = attempt.getId();
        }
        long attempts = attemptRepository.countByQuizIdAndUserId(quiz.getId(), user.getId());
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id", quiz.getId()); response.put("contentItemId", itemId); response.put("passingScore", quiz.getPassingScore()); response.put("maxAttempts", quiz.getMaxAttempts()); response.put("timeLimitSeconds", quiz.getTimeLimitSeconds()); response.put("attemptsUsed", attempts);
        response.put("randomizeQuestions", quiz.isRandomizeQuestions());
        response.put("randomizeOptions", quiz.isRandomizeOptions());
        response.put("questions", randomizedLearnerQuestions(quiz, randomSeed));
        return response;
    }

    @Transactional
    public Map<String, Object> startAttempt(String secret, Long courseId, Long itemId, Long moodleUserId) {
        log.info("Start attempt for course {}, item {} by user {}", courseId, itemId, moodleUserId);
        UserAccount user = verifyLearner(secret, courseId, moodleUserId);
        Quiz quiz = quizRepository.findByContentItemId(itemId)
                .orElseThrow(() -> {
                    log.info("Quiz not found");
                    new NotFoundException("Quiz not found");
                    return null;
                });
        courseContentAccessService.requireUnlocked(user, courseId, itemId);
        validateAvailableQuiz(quiz, courseId);
        progressService.start(secret, courseId, itemId, moodleUserId);
        Optional<QuizAttempt> existing = attemptRepository.findFirstByQuizIdAndUserIdAndStatusOrderByAttemptNumberDesc(quiz.getId(), user.getId(), QuizAttemptStatus.IN_PROGRESS);
        if (existing.isPresent() && !expired(existing.get()))
            return attemptResponse(existing.get(), false);

        long used = attemptRepository.countByQuizIdAndUserId(quiz.getId(), user.getId());

        if (quiz.getMaxAttempts() != null && used >= quiz.getMaxAttempts())
            throw new ForbiddenException("No quiz attempts remaining");
        QuizAttempt attempt = new QuizAttempt();
        attempt.setQuiz(quiz);
        attempt.setUser(user);
        attempt.setAttemptNumber((int) used + 1);
        attempt.setStartedAt(Instant.now());
        if (quiz.getTimeLimitSeconds() != null) attempt.setExpiresAt(Instant.now().plusSeconds(quiz.getTimeLimitSeconds()));
        return attemptResponse(attemptRepository.save(attempt), false);
    }

    @Transactional
    public Map<String, Object> submitAttempt(String secret, Long courseId, Long itemId, Long attemptId, MoodleQuizAttemptRequest request) {
        int answerCount = request.answers() == null ? 0 : request.answers().size();
        log.info("Quiz submit started: courseId={}, itemId={}, attemptId={}, moodleUserId={}, answerCount={}",
                courseId, itemId, attemptId, request.moodleUserId(), answerCount);
        try {
            UserAccount user = verifyLearner(secret, courseId, request.moodleUserId());
            log.info("Quiz submit learner verified: attemptId={}, userId={}", attemptId, user.getId());

            QuizAttempt attempt = attemptRepository.findById(attemptId)
                    .orElseThrow(() -> new NotFoundException("Quiz attempt not found"));
            log.info("Quiz submit attempt loaded: attemptId={}, status={}, quizId={}, attemptUserId={}",
                    attemptId, attempt.getStatus(), attempt.getQuiz().getId(), attempt.getUser().getId());

            if (!attempt.getQuiz().getContentItem().getId().equals(itemId) || !attempt.getUser().getId().equals(user.getId())) {
                throw new NotFoundException("Quiz attempt not found");
            }
            log.info("Quiz submit attempt ownership verified: attemptId={}", attemptId);
            if (attempt.getStatus() != QuizAttemptStatus.IN_PROGRESS) {
                log.info("Quiz submit returned existing result: attemptId={}, status={}", attemptId, attempt.getStatus());
                return attemptResponse(attempt, true);
            }
            if (expired(attempt)) {
                attempt.setStatus(QuizAttemptStatus.EXPIRED);
                attemptRepository.save(attempt);
                log.warn("Quiz submit rejected because attempt expired: attemptId={}", attemptId);
                throw new ForbiddenException("Quiz attempt has expired");
            }

            List<QuizQuestion> questions = questionRepository.findAllByQuizIdOrderByOrderIndexAscIdAsc(attempt.getQuiz().getId());
            log.info("Quiz submit questions loaded: attemptId={}, questionCount={}", attemptId, questions.size());
            Map<Long, Set<Long>> submitted = new HashMap<>();
            for (MoodleQuizAnswerRequest answer : request.answers() == null ? List.<MoodleQuizAnswerRequest>of() : request.answers()) {
                if (answer.questionId() != null) {
                    submitted.put(answer.questionId(), new HashSet<>(answer.optionIds() == null ? List.of() : answer.optionIds()));
                }
            }
            log.info("Quiz submit answers normalized: attemptId={}, answeredQuestionCount={}", attemptId, submitted.size());
            Map<Long, List<QuizOption>> options = optionRepository.findAllByQuestionIdInOrderByOrderIndexAscIdAsc(questions.stream().map(QuizQuestion::getId).toList()).stream().collect(Collectors.groupingBy(value -> value.getQuestion().getId()));
            log.info("Quiz submit options loaded: attemptId={}, optionQuestionCount={}", attemptId, options.size());

            answerRepository.deleteByAttemptId(attempt.getId());
            log.info("Quiz submit previous answers cleared: attemptId={}", attemptId);
            int score = 0; int maxScore = 0;
            List<Map<String, Object>> review = new ArrayList<>();
            for (QuizQuestion question : questions) {
                Set<Long> selected = submitted.getOrDefault(question.getId(), Set.of());
                List<QuizOption> questionOptions = options.getOrDefault(question.getId(), List.of());
                Set<Long> valid = questionOptions.stream().map(QuizOption::getId).collect(Collectors.toSet());
                if (!valid.containsAll(selected)) {
                    log.warn("Quiz submit rejected invalid option: attemptId={}, questionId={}, selectedOptionCount={}",
                            attemptId, question.getId(), selected.size());
                    throw new IllegalArgumentException("Invalid quiz option selected");
                }
                Set<Long> correct = questionOptions.stream().filter(QuizOption::isCorrect).map(QuizOption::getId).collect(Collectors.toSet());
                boolean correctAnswer = selected.equals(correct);
                int earned = correctAnswer ? question.getPoints() : 0;
                score += earned; maxScore += question.getPoints();
                QuizAttemptAnswer answer = new QuizAttemptAnswer(); answer.setAttempt(attempt); answer.setQuestion(question); answer.setSelectedOptionIds(selected.stream().sorted().map(String::valueOf).collect(Collectors.joining(","))); answerRepository.save(answer);
                log.info("Quiz submit answer saved: attemptId={}, questionId={}, selectedOptionCount={}, earnedPoints={}",
                        attemptId, question.getId(), selected.size(), earned);
                Map<String, Object> itemReview = new LinkedHashMap<>(); itemReview.put("questionId", question.getId()); itemReview.put("correct", correctAnswer); itemReview.put("earnedPoints", earned); itemReview.put("feedback", question.getFeedback()); itemReview.put("correctOptionIds", correct); review.add(itemReview);
            }

            attempt.setStatus(QuizAttemptStatus.SUBMITTED); attempt.setSubmittedAt(Instant.now()); attempt.setScore(score); attempt.setMaxScore(maxScore); attempt.setPassed(score >= attempt.getQuiz().getPassingScore()); attemptRepository.save(attempt);
            log.info("Quiz submit attempt graded and saved: attemptId={}, score={}, maxScore={}, passed={}",
                    attemptId, score, maxScore, attempt.getPassed());
            if (Boolean.TRUE.equals(attempt.getPassed())) {
                log.info("Quiz submit updating content progress: attemptId={}", attemptId);
                progressService.complete(secret, courseId, itemId, request.moodleUserId());
                log.info("Quiz submit content progress completed: attemptId={}", attemptId);
            }
            Map<String, Object> response = attemptResponse(attempt, true); response.put("questions", review);
            log.info("Quiz submit completed successfully: attemptId={}", attemptId);
            return response;
        } catch (RuntimeException exception) {
            log.error("Quiz submit failed: courseId={}, itemId={}, attemptId={}, moodleUserId={}, errorType={}, message={}",
                    courseId, itemId, attemptId, request.moodleUserId(), exception.getClass().getSimpleName(), exception.getMessage(), exception);
            throw exception;
        }
    }

    private QuizDefinitionResponse definition(Quiz quiz, boolean includeAnswers) {
        List<QuizQuestion> questions = questionRepository.findAllByQuizIdOrderByOrderIndexAscIdAsc(quiz.getId());
        Map<Long, List<QuizOption>> options = optionRepository.findAllByQuestionIdInOrderByOrderIndexAscIdAsc(questions.stream().map(QuizQuestion::getId).toList()).stream().collect(Collectors.groupingBy(value -> value.getQuestion().getId(), LinkedHashMap::new, Collectors.toList()));
        return new QuizDefinitionResponse(
                quiz.getId(), quiz.getContentItem().getId(), quiz.getPassingScore(), quiz.getMaxAttempts(),
                quiz.getTimeLimitSeconds(), quiz.getOpensAt(), quiz.getClosesAt(), quiz.isPublished(),
                quiz.isRandomizeQuestions(), quiz.isRandomizeOptions(),
                questions.stream().map(question -> new QuizQuestionResponse(
                        question.getId(), question.getType().name(), question.getPrompt(),
                        includeAnswers ? question.getFeedback() : null, question.getPoints(), question.getOrderIndex(),
                        options.getOrDefault(question.getId(), List.of()).stream()
                                .map(option -> new QuizOptionResponse(
                                        option.getId(), option.getText(), includeAnswers ? option.isCorrect() : null,
                                        option.getOrderIndex()))
                                .toList()))
                        .toList()
        );
    }

    private List<QuizQuestionResponse> randomizedLearnerQuestions(Quiz quiz, long randomSeed) {
        List<QuizQuestionResponse> questions = new ArrayList<>(definition(quiz, false).questions());
        if (quiz.isRandomizeOptions()) {
            questions = questions.stream().map(question -> {
                List<QuizOptionResponse> options = new ArrayList<>(question.options());
                Collections.shuffle(options, new Random(randomSeed ^ (question.id() * 1_000_003L)));
                return new QuizQuestionResponse(
                        question.id(), question.type(), question.prompt(), question.feedback(), question.points(),
                        question.orderIndex(), options);
            }).toList();
        }
        if (quiz.isRandomizeQuestions()) {
            questions = new ArrayList<>(questions);
            Collections.shuffle(questions, new Random(randomSeed));
        }
        return questions;
    }
    private Quiz loadQuiz(Long courseId, Long sectionId, Long itemId) { loadItem(courseId, sectionId, itemId); return quizRepository.findByContentItemId(itemId).orElseThrow(() -> new NotFoundException("Quiz not configured")); }
    private CourseContentItem loadItem(Long courseId, Long sectionId, Long itemId) { CourseContentItem item = itemRepository.findByIdAndSectionId(itemId, sectionId).orElseThrow(() -> new NotFoundException("Course content item not found")); if (!item.getSection().getCourseId().equals(courseId)) throw new NotFoundException("Course content item not found"); return item; }
    private void requireEditor(String authorization) { AuthSession s = resolve(authorization); if (s.getUser().getRole() != UserRole.ADMIN) throw new ForbiddenException("Admin access is required"); }
    private AuthSession resolve(String header) { if (header == null || !header.startsWith("Bearer ")) throw new ForbiddenException("Authentication is required"); AuthSession session = authSessionRepository.findByAccessToken(header.substring(7).trim()).orElseThrow(() -> new ForbiddenException("Authentication is required")); if (session.getExpiresAt().isBefore(Instant.now())) throw new ForbiddenException("Authentication is required"); return session; }
    private UserAccount verifyLearner(String secret, Long courseId, Long moodleUserId) { launchService.requirePluginSecret(secret); UserAccount user = mappingRepository.findByMoodleUserId(moodleUserId).orElseThrow(() -> new NotFoundException("Moodle user mapping was not found")).getUser(); if (user.getRole() != UserRole.ADMIN && !enrollmentRepository.existsByUserIdAndCourseId(user.getId(), courseId)) throw new ForbiddenException("User is not enrolled in this course"); return user; }
    private void validateAvailableQuiz(Quiz quiz, Long courseId) { if (!quiz.getContentItem().getSection().getCourseId().equals(courseId) || !quiz.isPublished()) throw new NotFoundException("Quiz not available"); validateAvailability(quiz); }
    private void validateAvailability(Quiz quiz) { Instant now = Instant.now(); if (quiz.getOpensAt() != null && now.isBefore(quiz.getOpensAt())) throw new ForbiddenException("Quiz is not open yet"); if (quiz.getClosesAt() != null && now.isAfter(quiz.getClosesAt())) throw new ForbiddenException("Quiz is closed"); }
    private boolean expired(QuizAttempt attempt) { return attempt.getExpiresAt() != null && Instant.now().isAfter(attempt.getExpiresAt()); }
    private Map<String, Object> attemptResponse(QuizAttempt attempt, boolean review) { Map<String, Object> response = new LinkedHashMap<>(); response.put("attemptId", attempt.getId()); response.put("attemptNumber", attempt.getAttemptNumber()); response.put("status", attempt.getStatus().name()); response.put("expiresAt", attempt.getExpiresAt()); response.put("score", attempt.getScore()); response.put("maxScore", attempt.getMaxScore()); response.put("passed", attempt.getPassed()); response.put("review", review); return response; }
    private void validateDefinition(QuizDefinitionRequest r) { if (r == null || r.questions() == null || r.questions().isEmpty()) throw new IllegalArgumentException("Quiz must contain at least one question"); if (r.passingScore() != null && r.passingScore() < 0) throw new IllegalArgumentException("Passing score must not be negative"); if (r.maxAttempts() != null && r.maxAttempts() < 1) throw new IllegalArgumentException("Max attempts must be at least one"); if (r.timeLimitSeconds() != null && r.timeLimitSeconds() < 1) throw new IllegalArgumentException("Time limit must be positive"); if (r.opensAt() != null && r.closesAt() != null && !r.opensAt().isBefore(r.closesAt())) throw new IllegalArgumentException("Quiz close time must be after open time"); for (QuizQuestionRequest q : r.questions()) { if (q == null || q.type() == null || trim(q.prompt()) == null || q.points() == null || q.points() < 1 || q.options() == null || q.options().size() < 2) throw new IllegalArgumentException("Each question requires prompt, points and at least two options"); long correct = q.options().stream().filter(o -> o != null && Boolean.TRUE.equals(o.correct())).count(); if ((q.type() == QuizQuestionType.SINGLE_CHOICE || q.type() == QuizQuestionType.TRUE_FALSE) && correct != 1) throw new IllegalArgumentException("Single choice and true/false require one correct option"); if (q.type() == QuizQuestionType.MULTIPLE_CHOICE && correct < 1) throw new IllegalArgumentException("Multiple choice requires a correct option"); if (q.options().stream().anyMatch(o -> o == null || trim(o.text()) == null)) throw new IllegalArgumentException("Option text is required"); } }
    private String trim(String value) { if (value == null) return null; String trimmed = value.trim(); return trimmed.isEmpty() ? null : trimmed; }
}
