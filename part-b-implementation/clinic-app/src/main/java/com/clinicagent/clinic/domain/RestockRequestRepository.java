package com.clinicagent.clinic.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RestockRequestRepository extends JpaRepository<RestockRequest, Long> {

    List<RestockRequest> findAllByOrderByCreatedAtDesc();
}
