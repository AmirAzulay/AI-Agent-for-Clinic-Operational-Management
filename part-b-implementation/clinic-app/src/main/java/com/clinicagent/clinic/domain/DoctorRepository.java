package com.clinicagent.clinic.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DoctorRepository extends JpaRepository<Doctor, Long> {

    List<Doctor> findAllByOrderByIdAsc();

    List<Doctor> findByDepartmentIdOrderByIdAsc(Long departmentId);
}
