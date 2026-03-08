package com.linkscript.domain.dto;

import com.linkscript.domain.entity.ScriptStatus;

public record IngestScriptResponse(String scriptUuid, ScriptStatus status) {
}
