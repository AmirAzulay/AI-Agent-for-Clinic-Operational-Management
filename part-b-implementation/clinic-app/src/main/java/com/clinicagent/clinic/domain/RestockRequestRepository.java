package com.clinicagent.clinic.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RestockRequestRepository extends JpaRepository<RestockRequest, Long> {

    List<RestockRequest> findAllByOrderByCreatedAtDesc();

    boolean existsByMedicationIdAndStatus(Long medicationId, com.clinicagent.clinic.model.RestockStatus status);

    List<RestockRequest> findByRunIdAndStatusAndDeliverAtLessThanEqualOrderByIdAsc(
            Long runId, com.clinicagent.clinic.model.RestockStatus status, java.time.Instant now);

    List<RestockRequest> findByStatusOrderByIdAsc(com.clinicagent.clinic.model.RestockStatus status);
}
