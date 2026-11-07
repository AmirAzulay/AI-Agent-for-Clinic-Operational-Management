package com.clinicagent.clinic.domain;

import com.clinicagent.clinic.model.PatientStatus;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PatientRepository extends JpaRepository<Patient, Long>, JpaSpecificationExecutor<Patient> {

    List<Patient> findByStatusInOrderByArrivedAtAsc(Collection<PatientStatus> statuses);

    List<Patient> findByStatusOrderByIdAsc(PatientStatus status);

    /** Waiting patients whose wait has exceeded their patience at {@code now}. Patients without a patience never qualify. */
    @Query(value = """
            select * from patients
            where status = 'WAITING'
              and patience_min is not null
              and arrived_at + make_interval(mins => patience_min) < :now
            order by arrived_at
            """, nativeQuery = true)
    List<Patient> findOutOfPatience(@Param("now") Instant now);

    /** Per department: patients who arrived since {@code since}, and how many of them left. */
    @Query(value = """
            select department_id                                  as departmentId,
                   count(*)                                       as arrivals,
                   count(*) filter (where status = 'LEFT')        as walkouts
            from patients
            where arrived_at >= :since
            group by department_id
            """, nativeQuery = true)
    List<WalkoutCounts> countWalkoutsSince(@Param("since") Instant since);

    interface WalkoutCounts {
        Long getDepartmentId();

        long getArrivals();

        long getWalkouts();

        default double rate() {
            return getArrivals() == 0 ? 0.0 : (double) getWalkouts() / getArrivals();
        }
    }
}
