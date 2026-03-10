package com.linkscript.api;

import com.linkscript.core.tag.TagService;
import com.linkscript.domain.dto.TagDto;
import com.linkscript.domain.entity.TagCategory;
import com.linkscript.domain.entity.TagEntity;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class TagController {

    private final TagService tagService;

    public TagController(TagService tagService) {
        this.tagService = tagService;
    }

    @GetMapping("/tags")
    public List<TagDto> listTags(@RequestParam(required = false) TagCategory category) {
        List<TagEntity> tags = tagService.getAllTags(category);
        return tags.stream()
                .map(t -> new TagDto(t.getId(), t.getName(), t.getCategory().name(), null))
                .toList();
    }

    @PostMapping("/tags")
    @ResponseStatus(HttpStatus.CREATED)
    public TagDto createTag(@RequestBody CreateTagRequest request) {
        TagEntity tag = tagService.findOrCreate(request.name(), request.category());
        return new TagDto(tag.getId(), tag.getName(), tag.getCategory().name(), null);
    }

    @GetMapping("/scripts/{uuid}/tags")
    public List<TagDto> getScriptTags(@PathVariable String uuid) {
        return tagService.getTagsByScript(uuid);
    }

    @PostMapping("/scripts/{uuid}/tags")
    @ResponseStatus(HttpStatus.CREATED)
    public void addScriptTags(@PathVariable String uuid, @RequestBody AddTagsRequest request) {
        tagService.tagScript(uuid, request.tags(), "MANUAL");
    }

    @DeleteMapping("/scripts/{uuid}/tags/{tagId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeScriptTag(@PathVariable String uuid, @PathVariable Long tagId) {
        tagService.removeTag(uuid, tagId);
    }

    public record CreateTagRequest(String name, TagCategory category) {
    }

    public record AddTagsRequest(Map<TagCategory, List<String>> tags) {
    }
}
