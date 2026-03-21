package com.linkscript.core.tag;

import com.linkscript.domain.dto.TagDto;
import com.linkscript.domain.entity.ScriptTagEntity;
import com.linkscript.domain.entity.TagCategory;
import com.linkscript.domain.entity.TagEntity;
import com.linkscript.domain.repository.ScriptTagRepository;
import com.linkscript.domain.repository.TagRepository;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TagService {

    private static final Logger log = LoggerFactory.getLogger(TagService.class);

    private final TagRepository tagRepository;
    private final ScriptTagRepository scriptTagRepository;

    public TagService(TagRepository tagRepository, ScriptTagRepository scriptTagRepository) {
        this.tagRepository = tagRepository;
        this.scriptTagRepository = scriptTagRepository;
    }

    @Transactional
    public TagEntity findOrCreate(String name, TagCategory category) {
        return tagRepository.findByNameAndCategory(name, category)
                .orElseGet(() -> {
                    TagEntity tag = new TagEntity();
                    tag.setName(name);
                    tag.setCategory(category);
                    TagEntity saved = tagRepository.save(tag);
                    log.info("tag.created name={} category={} id={}", name, category, saved.getId());
                    return saved;
                });
    }

    @Transactional
    public void tagScript(String scriptUuid, Map<TagCategory, List<String>> tagsByCategory, String source) {
        if (tagsByCategory == null || tagsByCategory.isEmpty()) {
            return;
        }
        for (Map.Entry<TagCategory, List<String>> entry : tagsByCategory.entrySet()) {
            TagCategory category = entry.getKey();
            for (String tagName : entry.getValue()) {
                String trimmed = tagName.trim();
                if (trimmed.isEmpty())
                    continue;

                TagEntity tag = findOrCreate(trimmed, category);
                ScriptTagEntity scriptTag = new ScriptTagEntity();
                scriptTag.setScriptUuid(scriptUuid);
                scriptTag.setTagId(tag.getId());
                scriptTag.setSource(source);

                try {
                    scriptTagRepository.save(scriptTag);
                    log.info("tag.linked scriptUuid={} tagId={} tagName={} source={}", scriptUuid, tag.getId(), trimmed,
                            source);
                } catch (Exception e) {
                    log.debug("tag.already_linked scriptUuid={} tagId={}", scriptUuid, tag.getId());
                }
            }
        }
    }

    @Transactional(readOnly = true)
    public List<TagDto> getTagsByScript(String scriptUuid) {
        List<ScriptTagEntity> scriptTags = scriptTagRepository.findByScriptUuid(scriptUuid);
        if (scriptTags.isEmpty()) {
            return List.of();
        }
        List<Long> tagIds = scriptTags.stream().map(ScriptTagEntity::getTagId).toList();
        Map<Long, String> sourceMap = new java.util.HashMap<>();
        for (ScriptTagEntity st : scriptTags) {
            sourceMap.put(st.getTagId(), st.getSource());
        }

        List<TagEntity> tags = tagRepository.findByIdIn(tagIds);
        List<TagDto> result = new ArrayList<>();
        for (TagEntity tag : tags) {
            result.add(new TagDto(tag.getId(), tag.getName(), tag.getCategory().name(),
                    sourceMap.getOrDefault(tag.getId(), "AI")));
        }
        result.sort(Comparator.comparing(TagDto::category).thenComparing(TagDto::name));
        return result;
    }

    @Transactional(readOnly = true)
    public Map<String, List<TagDto>> getTagsByScripts(Collection<String> scriptUuids) {
        if (scriptUuids == null || scriptUuids.isEmpty()) {
            return Map.of();
        }

        List<ScriptTagEntity> scriptTags = scriptTagRepository.findByScriptUuidIn(scriptUuids);
        if (scriptTags.isEmpty()) {
            return Map.of();
        }

        List<Long> tagIds = scriptTags.stream()
                .map(ScriptTagEntity::getTagId)
                .distinct()
                .toList();
        Map<Long, TagEntity> tagsById = tagRepository.findByIdIn(tagIds).stream()
                .collect(java.util.stream.Collectors.toMap(TagEntity::getId, tag -> tag));

        Map<String, List<TagDto>> result = new LinkedHashMap<>();
        for (ScriptTagEntity scriptTag : scriptTags) {
            TagEntity tag = tagsById.get(scriptTag.getTagId());
            if (tag == null) {
                continue;
            }
            result.computeIfAbsent(scriptTag.getScriptUuid(), ignored -> new ArrayList<>())
                    .add(new TagDto(tag.getId(), tag.getName(), tag.getCategory().name(), scriptTag.getSource()));
        }

        result.values().forEach(tags -> tags.sort(Comparator
                .comparing(TagDto::category)
                .thenComparing(TagDto::name)));
        return result;
    }

    @Transactional(readOnly = true)
    public List<TagEntity> getAllTags(TagCategory category) {
        if (category != null) {
            return tagRepository.findByCategory(category);
        }
        return tagRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<String> findScriptUuidsByTagNames(Collection<String> tagNames) {
        List<TagEntity> tags = new ArrayList<>();
        for (String name : tagNames) {
            String trimmed = name == null ? "" : name.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            tagRepository.findByNameAndCategory(trimmed, TagCategory.INDUSTRY).ifPresent(tags::add);
            tagRepository.findByNameAndCategory(trimmed, TagCategory.EMOTION).ifPresent(tags::add);
            tagRepository.findByNameAndCategory(trimmed, TagCategory.AUDIENCE).ifPresent(tags::add);
            tagRepository.findByNameAndCategory(trimmed, TagCategory.PLATFORM).ifPresent(tags::add);
            tagRepository.findByNameAndCategory(trimmed, TagCategory.STYLE).ifPresent(tags::add);
        }
        if (tags.isEmpty()) {
            return List.of();
        }
        List<Long> tagIds = tags.stream().map(TagEntity::getId).toList();
        return scriptTagRepository.findScriptUuidsByTagIdIn(tagIds);
    }

    @Transactional
    public void removeTag(String scriptUuid, Long tagId) {
        scriptTagRepository.deleteByScriptUuidAndTagId(scriptUuid, tagId);
        log.info("tag.removed scriptUuid={} tagId={}", scriptUuid, tagId);
    }
}
