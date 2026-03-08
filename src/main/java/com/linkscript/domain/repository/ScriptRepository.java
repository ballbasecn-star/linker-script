package com.linkscript.domain.repository;

import com.linkscript.domain.entity.ScriptEntity;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScriptRepository extends JpaRepository<ScriptEntity, Long> {

    Optional<ScriptEntity> findByScriptUuid(String scriptUuid);

    Optional<ScriptEntity> findFirstBySourcePlatformAndExternalId(String sourcePlatform, String externalId);

    List<ScriptEntity> findAllByScriptUuidIn(Collection<String> scriptUuids);
}
