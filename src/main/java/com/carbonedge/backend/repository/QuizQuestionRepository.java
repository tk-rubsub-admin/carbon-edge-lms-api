package com.carbonedge.backend.repository;
import com.carbonedge.backend.model.QuizQuestion; import java.util.List; import org.springframework.data.jpa.repository.JpaRepository;
public interface QuizQuestionRepository extends JpaRepository<QuizQuestion, Long> { List<QuizQuestion> findAllByQuizIdOrderByOrderIndexAscIdAsc(Long quizId); void deleteByQuizId(Long quizId); }
