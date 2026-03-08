package com.linkscript.domain.repository;

import com.linkscript.domain.entity.GenerationLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GenerationLogRepository extends JpaRepository<GenerationLogEntity, Long> {
}
