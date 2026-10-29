package com.carbonedge.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.carbonedge.backend.dto.QuizOptionResponse;
import com.carbonedge.backend.dto.QuizQuestionResponse;
import com.carbonedge.backend.model.CourseContentItem;
import com.carbonedge.backend.model.CourseSection;
import com.carbonedge.backend.model.MoodleUserMapping;
import com.carbonedge.backend.model.Quiz;
import com.carbonedge.backend.model.QuizAttempt;
import com.carbonedge.backend.model.QuizOption;
import com.carbonedge.backend.model.QuizQuestion;
import com.carbonedge.backend.model.QuizQuestionType;
import com.carbonedge.backend.model.UserAccount;
import com.carbonedge.backend.model.UserRole;
import com.carbonedge.backend.repository.AuthSessionRepository;
import com.carbonedge.backend.repository.CourseContentItemRepository;
import com.carbonedge.backend.repository.CourseStudentEnrollmentRepository;
import com.carbonedge.backend.repository.MoodleUserMappingRepository;
import com.carbonedge.backend.repository.QuizAttemptAnswerRepository;
import com.carbonedge.backend.repository.QuizAttemptRepository;
import com.carbonedge.backend.repository.QuizOptionRepository;
import com.carbonedge.backend.repository.QuizQuestionRepository;
import com.carbonedge.backend.repository.QuizRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class QuizRandomizationServiceTests {

    private Quiz quiz;
    private QuizService service;

    @BeforeEach
    void setUp() {
        CourseStudentEnrollmentRepository enrollmentRepository = mock(CourseStudentEnrollmentRepository.class);
        MoodleUserMappingRepository mappingRepository = mock(MoodleUserMappingRepository.class);
        QuizRepository quizRepository = mock(QuizRepository.class);
        QuizQuestionRepository questionRepository = mock(QuizQuestionRepository.class);
        QuizOptionRepository optionRepository = mock(QuizOptionRepository.class);
        QuizAttemptRepository attemptRepository = mock(QuizAttemptRepository.class);

        service = new QuizService(
                mock(AuthSessionRepository.class),
                mock(CourseContentItemRepository.class),
                enrollmentRepository,
                mappingRepository,
                quizRepository,
                questionRepository,
                optionRepository,
                attemptRepository,
                mock(QuizAttemptAnswerRepository.class),
                mock(MoodleLaunchService.class),
                mock(MoodleContentProgressService.class),
                mock(CourseContentAccessService.class));

        UserAccount user = mock(UserAccount.class);
        when(user.getId()).thenReturn(7L);
        when(user.getRole()).thenReturn(UserRole.STUDENT);
        MoodleUserMapping mapping = mock(MoodleUserMapping.class);
        when(mapping.getUser()).thenReturn(user);
        when(mappingRepository.findByMoodleUserId(99L)).thenReturn(Optional.of(mapping));
        when(enrollmentRepository.existsByUserIdAndCourseId(7L, 11L)).thenReturn(true);

        CourseSection section = mock(CourseSection.class);
        when(section.getCourseId()).thenReturn(11L);
        CourseContentItem item = mock(CourseContentItem.class);
        when(item.getId()).thenReturn(23L);
        when(item.getSection()).thenReturn(section);

        quiz = mock(Quiz.class);
        when(quiz.getId()).thenReturn(31L);
        when(quiz.getContentItem()).thenReturn(item);
        when(quiz.isPublished()).thenReturn(true);
        when(quiz.isRandomizeQuestions()).thenReturn(true);
        when(quiz.isRandomizeOptions()).thenReturn(true);
        when(quizRepository.findByContentItemId(23L)).thenReturn(Optional.of(quiz));

        QuizAttempt attempt = mock(QuizAttempt.class);
        when(attempt.getId()).thenReturn(101L);
        when(attempt.getQuiz()).thenReturn(quiz);
        when(attempt.getUser()).thenReturn(user);
        when(attemptRepository.findById(101L)).thenReturn(Optional.of(attempt));
        QuizAttempt nextAttempt = mock(QuizAttempt.class);
        when(nextAttempt.getId()).thenReturn(102L);
        when(nextAttempt.getQuiz()).thenReturn(quiz);
        when(nextAttempt.getUser()).thenReturn(user);
        when(attemptRepository.findById(102L)).thenReturn(Optional.of(nextAttempt));

        List<QuizQuestion> questions = new ArrayList<>();
        List<QuizOption> options = new ArrayList<>();
        for (long questionId = 1; questionId <= 5; questionId++) {
            QuizQuestion question = mock(QuizQuestion.class);
            when(question.getId()).thenReturn(questionId);
            when(question.getType()).thenReturn(QuizQuestionType.SINGLE_CHOICE);
            when(question.getPrompt()).thenReturn("Question " + questionId);
            when(question.getPoints()).thenReturn(1);
            when(question.getOrderIndex()).thenReturn((int) questionId);
            questions.add(question);
            for (long optionIndex = 1; optionIndex <= 3; optionIndex++) {
                QuizOption option = mock(QuizOption.class);
                long optionId = questionId * 10 + optionIndex;
                when(option.getId()).thenReturn(optionId);
                when(option.getQuestion()).thenReturn(question);
                when(option.getText()).thenReturn("Option " + optionId);
                when(option.getOrderIndex()).thenReturn((int) optionIndex);
                options.add(option);
            }
        }
        when(questionRepository.findAllByQuizIdOrderByOrderIndexAscIdAsc(31L)).thenReturn(questions);
        when(optionRepository.findAllByQuestionIdInOrderByOrderIndexAscIdAsc(List.of(1L, 2L, 3L, 4L, 5L)))
                .thenReturn(options);
    }

    @Test
    @SuppressWarnings("unchecked")
    void learnerOrderIsRandomizedButStableForTheSameAttempt() {
        Map<String, Object> firstResponse = service.learnerDefinition("secret", 11L, 23L, 99L, 101L);
        Map<String, Object> secondResponse = service.learnerDefinition("secret", 11L, 23L, 99L, 101L);
        Map<String, Object> nextAttemptResponse = service.learnerDefinition("secret", 11L, 23L, 99L, 102L);

        List<QuizQuestionResponse> first = (List<QuizQuestionResponse>) firstResponse.get("questions");
        List<QuizQuestionResponse> second = (List<QuizQuestionResponse>) secondResponse.get("questions");
        List<QuizQuestionResponse> nextAttempt =
                (List<QuizQuestionResponse>) nextAttemptResponse.get("questions");

        assertThat(first.stream().map(QuizQuestionResponse::id).toList())
                .isEqualTo(second.stream().map(QuizQuestionResponse::id).toList())
                .isNotEqualTo(List.of(1L, 2L, 3L, 4L, 5L));
        for (int index = 0; index < first.size(); index++) {
            assertThat(first.get(index).options().stream().map(QuizOptionResponse::id).toList())
                    .isEqualTo(second.get(index).options().stream().map(QuizOptionResponse::id).toList());
        }
        assertThat(first).anySatisfy(question -> assertThat(
                question.options().stream().map(QuizOptionResponse::id).toList())
                .isNotEqualTo(List.of(question.id() * 10 + 1, question.id() * 10 + 2, question.id() * 10 + 3)));
        assertThat(orderSignature(first)).isNotEqualTo(orderSignature(nextAttempt));
        assertThat(firstResponse).containsEntry("randomizeQuestions", true).containsEntry("randomizeOptions", true);
    }

    private List<String> orderSignature(List<QuizQuestionResponse> questions) {
        return questions.stream()
                .map(question -> question.id() + ":" + question.options().stream()
                        .map(option -> option.id().toString())
                        .reduce((left, right) -> left + "," + right)
                        .orElse(""))
                .toList();
    }
}
