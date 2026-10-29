package com.carbonedge.backend.repository;
import com.carbonedge.backend.model.QuizOption; import java.util.Collection; import java.util.List; import org.springframework.data.jpa.repository.JpaRepository;
public interface QuizOptionRepository extends JpaRepository<QuizOption, Long> { List<QuizOption> findAllByQuestionIdInOrderByOrderIndexAscIdAsc(Collection<Long> questionIds); void deleteByQuestionIdIn(Collection<Long> questionIds); }
