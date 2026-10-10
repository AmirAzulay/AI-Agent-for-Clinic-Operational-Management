package com.clinicagent.clinic.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MedicationRepository extends JpaRepository<Medication, Long> {

    List<Medication> findAllByOrderByIdAsc();
}
