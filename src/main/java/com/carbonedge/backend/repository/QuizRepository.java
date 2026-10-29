package com.carbonedge.backend.repository;
import com.carbonedge.backend.model.Quiz; import java.util.Optional; import org.springframework.data.jpa.repository.JpaRepository;
public interface QuizRepository extends JpaRepository<Quiz, Long> { Optional<Quiz> findByContentItemId(Long contentItemId); void deleteByContentItemId(Long contentItemId); }
