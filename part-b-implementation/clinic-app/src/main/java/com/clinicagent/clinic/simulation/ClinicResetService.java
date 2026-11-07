package com.clinicagent.clinic.simulation;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Puts the clinic back in its initial state before a run: no patients, rooms, doctors and stock as
 * seeded, every department NORMAL, open alerts resolved. Then applies the scenario. History
 * (snapshots, AI decisions) is kept.
 */
@Service
public class ClinicResetService {

    private final JdbcTemplate jdbc;

    public ClinicResetService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void reset(SimulationScenario scenario) {
        jdbc.update("delete from patients");
        jdbc.update("update treatment_rooms set status = initial_status");
        jdbc.update("update doctors set available = initially_available");
        jdbc.update("update medications set stock = initial_stock");
        jdbc.update("update departments set status = 'NORMAL'");
        jdbc.update("update alerts set active = false, resolved_at = now() where active");

        if (scenario.oneDoctorOffPerDepartment()) {
            jdbc.update("""
                    update doctors set available = false
                    where id in (select min(id) from doctors group by department_id)
                    """);
        }
        if (scenario.lowMedicationStock()) {
            jdbc.update("update medications set stock = threshold + 5");
        }
    }
}
