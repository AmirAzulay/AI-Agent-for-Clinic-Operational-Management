package com.clinicagent.clinic.simulation;

import com.clinicagent.clinic.PostgresIntegrationTest;
import com.clinicagent.clinic.domain.Department;
import com.clinicagent.clinic.domain.DepartmentRepository;
import com.clinicagent.clinic.domain.Patient;
import com.clinicagent.clinic.domain.PatientRepository;
import com.clinicagent.clinic.model.PatientStatus;
import com.clinicagent.clinic.model.Urgency;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Walkouts against a real PostgreSQL. Each test runs in a transaction that is rolled back. */
@Transactional
class WalkoutServiceTest extends PostgresIntegrationTest {

    @Autowired
    WalkoutService walkouts;

    @Autowired
    PatientRepository patients;

    @Autowired
    DepartmentRepository departments;

    @Autowired
    JdbcTemplate jdbc;

    Department generalMedicine;
    Department pediatrics;
    Instant now;

    @BeforeEach
    void setUp() {
        List<Department> all = departments.findAllByOrderByIdAsc();
        generalMedicine = all.get(0);
        pediatrics = all.get(1);
        now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
    }

    private Patient arrived(Department department, Urgency urgency, Duration ago, Integer patienceMin) {
        return patients.save(new Patient(department, urgency, now.minus(ago), patienceMin));
    }

    @Test
    void patientsWhoWaitedLongerThanTheirPatienceLeave() {
        Patient impatient = arrived(generalMedicine, Urgency.LOW, Duration.ofMinutes(50), 45);
        Patient stillWilling = arrived(generalMedicine, Urgency.LOW, Duration.ofMinutes(40), 45);
        Patient alsoImpatient = arrived(pediatrics, Urgency.MEDIUM, Duration.ofMinutes(61), 60);

        Map<Long, Integer> result = walkouts.processWalkouts(now);

        assertThat(result).containsExactlyInAnyOrderEntriesOf(Map.of(
                generalMedicine.getId(), 1,
                pediatrics.getId(), 1));
        assertThat(impatient.getStatus()).isEqualTo(PatientStatus.LEFT);
        assertThat(impatient.getLeftAt()).isEqualTo(now);
        assertThat(impatient.waitingMinutes(now.plus(Duration.ofHours(1)))).isEqualTo(50.0);
        assertThat(alsoImpatient.getStatus()).isEqualTo(PatientStatus.LEFT);
        assertThat(stillWilling.getStatus()).isEqualTo(PatientStatus.WAITING);
    }

    @Test
    void patientsWithoutPatienceOrInTreatmentNeverLeave() {
        Patient high = arrived(generalMedicine, Urgency.HIGH, Duration.ofHours(5), null);
        Patient inTreatment = arrived(generalMedicine, Urgency.LOW, Duration.ofHours(2), 45);
        inTreatment.setStatus(PatientStatus.IN_TREATMENT);
        inTreatment.setTreatmentStartedAt(now.minus(Duration.ofHours(1)));

        Map<Long, Integer> result = walkouts.processWalkouts(now);

        assertThat(result).isEmpty();
        assertThat(high.getStatus()).isEqualTo(PatientStatus.WAITING);
        assertThat(inTreatment.getStatus()).isEqualTo(PatientStatus.IN_TREATMENT);
    }

    @Test
    void aSecondTickDoesNotCountTheSameWalkoutAgain() {
        arrived(generalMedicine, Urgency.LOW, Duration.ofMinutes(100), 45);

        assertThat(walkouts.processWalkouts(now)).containsEntry(generalMedicine.getId(), 1);
        patients.flush();
        assertThat(walkouts.processWalkouts(now.plusSeconds(10))).isEmpty();
    }

    @Test
    void walkoutRateIsWalkoutsOverArrivalsSinceTheGivenTime() {
        Instant runStart = now.minus(Duration.ofHours(3));
        arrived(generalMedicine, Urgency.LOW, Duration.ofMinutes(100), 45);   // leaves
        arrived(generalMedicine, Urgency.LOW, Duration.ofMinutes(10), 45);    // stays
        arrived(generalMedicine, Urgency.HIGH, Duration.ofMinutes(100), null); // stays
        arrived(generalMedicine, Urgency.MEDIUM, Duration.ofMinutes(130), 60); // leaves
        arrived(generalMedicine, Urgency.LOW, Duration.ofHours(4), 45);       // before the run: not counted
        walkouts.processWalkouts(now);
        patients.flush();

        PatientRepository.WalkoutCounts counts = walkouts.countsSince(runStart).get(generalMedicine.getId());

        assertThat(counts.getArrivals()).isEqualTo(4);
        assertThat(counts.getWalkouts()).isEqualTo(2);
        assertThat(counts.rate()).isEqualTo(0.5);
    }

    @Test
    void theDatabaseRejectsLeftWithoutLeftAt() {
        Patient patient = arrived(generalMedicine, Urgency.LOW, Duration.ofMinutes(5), 45);
        patients.flush();

        assertThatThrownBy(() -> jdbc.update("update patients set status = 'LEFT' where id = ?", patient.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
