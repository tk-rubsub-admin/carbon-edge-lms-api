package com.carbonedge.backend.repository;

import com.carbonedge.backend.model.MoodleUserMapping;
import com.carbonedge.backend.model.UserAccount;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MoodleUserMappingRepository extends JpaRepository<MoodleUserMapping, Long> {

    Optional<MoodleUserMapping> findByUser(UserAccount user);
}
