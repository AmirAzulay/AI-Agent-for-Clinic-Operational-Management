package com.clinicagent.clinic.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DepartmentRepository extends JpaRepository<Department, Long> {

    List<Department> findAllByOrderByIdAsc();
}
