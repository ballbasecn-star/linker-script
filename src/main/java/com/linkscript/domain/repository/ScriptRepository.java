package com.linkscript.domain.repository;

import com.linkscript.domain.entity.ScriptEntity;
import java.util.Collection;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScriptRepository extends JpaRepository<ScriptEntity, Long> {

    Optional<ScriptEntity> findByScriptUuid(String scriptUuid);

    Optional<ScriptEntity> findFirstBySourcePlatformAndExternalId(String sourcePlatform, String externalId);

    java.util.List<ScriptEntity> findAllByScriptUuidIn(Collection<String> scriptUuids);

    Page<ScriptEntity> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<ScriptEntity> findByHeatLevelIn(Collection<String> heatLevels, Pageable pageable);

    Page<ScriptEntity> findByScriptUuidIn(Collection<String> scriptUuids, Pageable pageable);

    Page<ScriptEntity> findByScriptUuidInAndHeatLevelIn(Collection<String> scriptUuids, Collection<String> heatLevels,
            Pageable pageable);
}
