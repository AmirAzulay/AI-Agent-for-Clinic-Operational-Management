package com.clinicagent.clinic.domain;

import com.clinicagent.common.model.PatientStatus;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface PatientRepository extends JpaRepository<Patient, Long>, JpaSpecificationExecutor<Patient> {

    List<Patient> findByStatusInOrderByArrivedAtAsc(Collection<PatientStatus> statuses);
}
