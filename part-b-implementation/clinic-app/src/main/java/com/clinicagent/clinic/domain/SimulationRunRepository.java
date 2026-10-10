package com.clinicagent.clinic.domain;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SimulationRunRepository extends JpaRepository<SimulationRun, Long> {

    Optional<SimulationRun> findFirstByStoppedAtIsNullOrderByStartedAtDesc();
}
