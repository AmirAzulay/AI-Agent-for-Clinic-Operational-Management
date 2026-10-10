package com.clinicagent.clinic.domain;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiScanRepository extends JpaRepository<AiScan, Long> {

    Optional<AiScan> findFirstByOrderByStartedAtDesc();
}
