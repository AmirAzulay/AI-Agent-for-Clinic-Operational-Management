package com.clinicagent.clinic.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {

    /** Pass an already normalized email ({@link UserAccount#normalizeEmail}). */
    Optional<UserAccount> findByEmail(String email);

    boolean existsByEmail(String email);

    List<UserAccount> findAllByOrderByIdAsc();
}
