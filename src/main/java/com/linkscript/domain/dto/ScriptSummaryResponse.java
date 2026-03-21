package com.linkscript.domain.dto;

import java.time.LocalDateTime;
import java.util.List;

public record ScriptSummaryResponse(
        String scriptUuid,
        String title,
        String sourcePlatform,
        String status,
        Double heatScore,
        String heatLevel,
        LocalDateTime createdAt,
        List<TagDto> tags) {
}
