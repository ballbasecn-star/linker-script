package com.linkscript.domain.repository;

import com.linkscript.domain.entity.FragmentType;
import com.linkscript.domain.entity.LogicFragmentEntity;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LogicFragmentRepository extends JpaRepository<LogicFragmentEntity, Long> {

    void deleteByScriptUuid(String scriptUuid);

    List<LogicFragmentEntity> findByScriptUuidOrderByIdAsc(String scriptUuid);

    List<LogicFragmentEntity> findByScriptUuidInOrderByScriptUuidAscIdAsc(Collection<String> scriptUuids);

    List<LogicFragmentEntity> findTop200ByOrderByIdDesc();

    List<LogicFragmentEntity> findTop200ByFragmentTypeOrderByIdDesc(FragmentType fragmentType);
}
