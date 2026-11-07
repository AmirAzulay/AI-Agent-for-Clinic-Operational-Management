package com.clinicagent.clinic.simulation;

import com.clinicagent.clinic.domain.Patient;
import com.clinicagent.clinic.domain.PatientRepository;
import com.clinicagent.clinic.domain.PatientRepository.WalkoutCounts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Patient walkouts (PLAN.md §3.4). Called by the simulation tick inside the tick's transaction:
 * every waiting patient who has waited longer than their patience leaves the queue.
 */
@Service
public class WalkoutService {

    private static final Logger log = LoggerFactory.getLogger(WalkoutService.class);

    private final PatientRepository patients;

    public WalkoutService(PatientRepository patients) {
        this.patients = patients;
    }

    /**
     * Marks every patient who ran out of patience at {@code now} as LEFT.
     *
     * @return walkouts in this call, per department id (departments without walkouts are absent)
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Map<Long, Integer> processWalkouts(Instant now) {
        List<Patient> leaving = patients.findOutOfPatience(now);
        Map<Long, Integer> perDepartment = new TreeMap<>();
        for (Patient patient : leaving) {
            patient.leave(now);
            perDepartment.merge(patient.getDepartment().getId(), 1, Integer::sum);
        }
        if (!leaving.isEmpty()) {
            log.info("{} patient(s) walked out: {}", leaving.size(), perDepartment);
        }
        return perDepartment;
    }

    /** Walkouts and arrivals per department id for patients who arrived since {@code since} (usually the run start). */
    @Transactional(readOnly = true)
    public Map<Long, WalkoutCounts> countsSince(Instant since) {
        return patients.countWalkoutsSince(since).stream()
                .collect(Collectors.toMap(WalkoutCounts::getDepartmentId, Function.identity()));
    }
}
