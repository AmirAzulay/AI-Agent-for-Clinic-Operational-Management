package com.clinicagent.clinic.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiActionRepository extends JpaRepository<AiAction, Long> {

    List<AiAction> findAllByOrderByCreatedAtDesc();
}
