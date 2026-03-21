package com.linkscript.domain.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.linkscript.domain.entity.ScriptStatus;
import java.time.LocalDateTime;
import java.util.List;

public record ScriptDetailResponse(
                String scriptUuid,
                String title,
                String content,
                String transcript,
                String sourcePlatform,
                String externalId,
                JsonNode statistics,
                ScriptStatus status,
                Double heatScore,
                String heatLevel,
                LocalDateTime createdAt,
                ScriptReviewDto review,
                List<LogicFragmentDto> fragments,
                List<TagDto> tags) {
}
