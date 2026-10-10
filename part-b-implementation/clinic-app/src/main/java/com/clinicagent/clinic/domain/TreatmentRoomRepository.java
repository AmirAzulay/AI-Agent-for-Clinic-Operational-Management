package com.clinicagent.clinic.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TreatmentRoomRepository extends JpaRepository<TreatmentRoom, Long> {

    List<TreatmentRoom> findAllByOrderByIdAsc();

    List<TreatmentRoom> findByDepartmentIdOrderByIdAsc(Long departmentId);
}
