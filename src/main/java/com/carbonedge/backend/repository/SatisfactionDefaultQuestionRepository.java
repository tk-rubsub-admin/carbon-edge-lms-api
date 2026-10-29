package com.carbonedge.backend.repository;

import com.carbonedge.backend.model.SatisfactionDefaultQuestion;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SatisfactionDefaultQuestionRepository extends JpaRepository<SatisfactionDefaultQuestion, Long> {
    List<SatisfactionDefaultQuestion> findAllByOrderByOrderIndexAsc();
}
