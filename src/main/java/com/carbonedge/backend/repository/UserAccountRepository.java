package com.carbonedge.backend.repository;

import com.carbonedge.backend.model.UserAccount;
import com.carbonedge.backend.model.UserRole;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {

    boolean existsByUsernameIgnoreCase(String username);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCaseAndIdNot(String email, Long id);

    Optional<UserAccount> findByEmailIgnoreCase(String email);

    Optional<UserAccount> findByUsernameIgnoreCase(String username);

    Optional<UserAccount> findByUsernameIgnoreCaseOrEmailIgnoreCase(String username, String email);

    @Query(value = """
            select userAccount
            from UserAccount userAccount
            where userAccount.role = :role
              and (
                :keyword is null
                or lower(userAccount.username) like lower(concat('%', :keyword, '%'))
                or lower(userAccount.email) like lower(concat('%', :keyword, '%'))
                or lower(userAccount.firstName) like lower(concat('%', :keyword, '%'))
                or lower(userAccount.lastName) like lower(concat('%', :keyword, '%'))
              )
            """,
            countQuery = """
            select count(userAccount)
            from UserAccount userAccount
            where userAccount.role = :role
              and (
                :keyword is null
                or lower(userAccount.username) like lower(concat('%', :keyword, '%'))
                or lower(userAccount.email) like lower(concat('%', :keyword, '%'))
                or lower(userAccount.firstName) like lower(concat('%', :keyword, '%'))
                or lower(userAccount.lastName) like lower(concat('%', :keyword, '%'))
              )
            """)
    Page<UserAccount> searchByRoleAndKeyword(
            @Param("role") UserRole role,
            @Param("keyword") String keyword,
            Pageable pageable
    );
}
