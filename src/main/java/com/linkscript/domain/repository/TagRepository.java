package com.linkscript.domain.repository;

import com.linkscript.domain.entity.TagCategory;
import com.linkscript.domain.entity.TagEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TagRepository extends JpaRepository<TagEntity, Long> {

    Optional<TagEntity> findByNameAndCategory(String name, TagCategory category);

    List<TagEntity> findByCategory(TagCategory category);

    List<TagEntity> findByIdIn(java.util.Collection<Long> ids);
}
