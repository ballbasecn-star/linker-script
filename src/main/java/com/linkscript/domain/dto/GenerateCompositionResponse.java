package com.linkscript.domain.dto;

import java.util.List;

public record GenerateCompositionResponse(
        Long logId,
        String topic,
        List<String> referenceUuids,
        String content
) {
}
