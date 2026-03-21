package com.linkscript.api;

import com.linkscript.core.script.ScriptService;
import com.linkscript.core.tag.TagService;
import com.linkscript.core.vector.VectorSearchService;
import com.linkscript.domain.dto.FragmentSearchResponse;
import com.linkscript.domain.dto.PageResponse;
import com.linkscript.domain.dto.ScriptDetailResponse;
import com.linkscript.domain.dto.ScriptSummaryResponse;
import com.linkscript.domain.dto.TagDto;
import com.linkscript.domain.entity.FragmentType;
import com.linkscript.domain.entity.LogicFragmentEntity;
import com.linkscript.domain.entity.ScriptEntity;
import com.linkscript.domain.repository.LogicFragmentRepository;
import com.linkscript.domain.repository.ScriptRepository;
import com.linkscript.infra.exception.NotFoundException;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1")
public class ScriptController {

    private final ScriptService scriptService;
    private final VectorSearchService vectorSearchService;
    private final LogicFragmentRepository logicFragmentRepository;
    private final TagService tagService;
    private final ScriptRepository scriptRepository;

    public ScriptController(
            ScriptService scriptService,
            VectorSearchService vectorSearchService,
            LogicFragmentRepository logicFragmentRepository,
            TagService tagService,
            ScriptRepository scriptRepository) {
        this.scriptService = scriptService;
        this.vectorSearchService = vectorSearchService;
        this.logicFragmentRepository = logicFragmentRepository;
        this.tagService = tagService;
        this.scriptRepository = scriptRepository;
    }

    @GetMapping("/scripts/{scriptUuid}")
    public ScriptDetailResponse getScript(@PathVariable String scriptUuid) {
        return scriptService.getDetail(scriptUuid);
    }

    @GetMapping("/scripts")
    public PageResponse<ScriptSummaryResponse> listScripts(
            @RequestParam(required = false) String tags,
            @RequestParam(required = false) String heatLevel,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        List<String> targetUuids = null;
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));

        if (tags != null && !tags.isBlank()) {
            List<String> tagNames = Arrays.stream(tags.split(","))
                    .map(String::trim)
                    .filter(value -> !value.isEmpty())
                    .toList();
            targetUuids = tagService.findScriptUuidsByTagNames(tagNames);
            if (targetUuids.isEmpty()) {
                return new PageResponse<>(List.of(), page, size, 0, 0, true, true);
            }
        }

        List<String> heatLevels = null;
        if (heatLevel != null && !heatLevel.isBlank()) {
            heatLevels = Arrays.stream(heatLevel.split(","))
                    .map(String::trim)
                    .filter(value -> !value.isEmpty())
                    .toList();
        }

        Page<ScriptEntity> scripts;
        if (targetUuids != null && heatLevels != null) {
            scripts = scriptRepository.findByScriptUuidInAndHeatLevelIn(targetUuids, heatLevels, pageable);
        } else if (targetUuids != null) {
            scripts = scriptRepository.findByScriptUuidIn(targetUuids, pageable);
        } else if (heatLevels != null) {
            scripts = scriptRepository.findByHeatLevelIn(heatLevels, pageable);
        } else {
            scripts = scriptRepository.findAllByOrderByCreatedAtDesc(pageable);
        }

        Map<String, List<TagDto>> tagsByScript = tagService.getTagsByScripts(
                scripts.getContent().stream().map(ScriptEntity::getScriptUuid).toList());
        List<ScriptSummaryResponse> content = scripts.getContent().stream()
                .map(s -> new ScriptSummaryResponse(
                        s.getScriptUuid(),
                        s.getTitle(),
                        s.getSourcePlatform(),
                        s.getStatus().name(),
                        s.getHeatScore(),
                        s.getHeatLevel(),
                        s.getCreatedAt(),
                        tagsByScript.getOrDefault(s.getScriptUuid(), List.of())))
                .toList();

        return new PageResponse<>(
                content,
                scripts.getNumber(),
                scripts.getSize(),
                scripts.getTotalElements(),
                scripts.getTotalPages(),
                scripts.isFirst(),
                scripts.isLast());
    }

    @GetMapping("/fragments/search")
    public List<FragmentSearchResponse> search(
            @RequestParam String topic,
            @RequestParam(required = false) FragmentType type,
            @RequestParam(defaultValue = "3") @Min(1) @Max(10) int limit) {
        return vectorSearchService.search(topic, type, limit);
    }

    @PutMapping("/fragments/{id}")
    public UpdateFragmentResponse updateFragment(@PathVariable Long id, @RequestBody UpdateFragmentRequest request) {
        LogicFragmentEntity fragment = logicFragmentRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Fragment not found: " + id));
        if (request.type() != null) {
            fragment.setFragmentType(request.type());
        }
        if (request.content() != null) {
            fragment.setContent(request.content());
        }
        if (request.logicDesc() != null) {
            fragment.setLogicDesc(request.logicDesc());
        }
        LogicFragmentEntity saved = logicFragmentRepository.save(fragment);
        return new UpdateFragmentResponse(saved.getId(), saved.getFragmentType(), saved.getContent(),
                saved.getLogicDesc());
    }

    public record UpdateFragmentRequest(FragmentType type, String content, String logicDesc) {
    }

    public record UpdateFragmentResponse(Long id, FragmentType type, String content, String logicDesc) {
    }
}
