package com.october.persistence;

import com.october.model.Job;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface JobRepository extends JpaRepository<Job, Long> {

    List<Job> findByCompanyIdOrderByTitleAsc(Long companyId);

    long deleteByCompanyId(Long companyId);
}
