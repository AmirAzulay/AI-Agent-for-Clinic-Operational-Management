package com.clinicagent.clinic.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SimulationRunRepository extends JpaRepository<SimulationRun, Long> {

    /** The most recent run, running or not. Its simulated clock is the clinic's "now". */
    Optional<SimulationRun> findFirstByOrderByIdDesc();

    List<SimulationRun> findByStoppedAtIsNull();
}
