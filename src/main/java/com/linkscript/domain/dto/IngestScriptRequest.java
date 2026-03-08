package com.linkscript.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Map;

public record IngestScriptRequest(
        @NotBlank @Size(max = 255) String title,
        @NotBlank String content,
        String transcript,
        @NotBlank @Size(max = 32) String sourcePlatform,
        @Size(max = 128) String externalId,
        Map<String, Object> statistics
) {
}
