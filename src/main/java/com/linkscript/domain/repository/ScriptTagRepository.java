package com.linkscript.domain.repository;

import com.linkscript.domain.entity.ScriptTagEntity;
import com.linkscript.domain.entity.ScriptTagId;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ScriptTagRepository extends JpaRepository<ScriptTagEntity, ScriptTagId> {

    List<ScriptTagEntity> findByScriptUuid(String scriptUuid);

    void deleteByScriptUuidAndTagId(String scriptUuid, Long tagId);

    @Query("SELECT DISTINCT st.scriptUuid FROM ScriptTagEntity st WHERE st.tagId IN :tagIds")
    List<String> findScriptUuidsByTagIdIn(Collection<Long> tagIds);
}
