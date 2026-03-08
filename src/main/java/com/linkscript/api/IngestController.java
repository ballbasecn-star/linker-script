package com.linkscript.api;

import com.linkscript.core.script.ScriptService;
import com.linkscript.domain.dto.IngestScriptRequest;
import com.linkscript.domain.dto.IngestScriptResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/scripts")
public class IngestController {

    private final ScriptService scriptService;

    public IngestController(ScriptService scriptService) {
        this.scriptService = scriptService;
    }

    @PostMapping("/ingest")
    public ResponseEntity<IngestScriptResponse> ingest(@Valid @RequestBody IngestScriptRequest request) {
        return ResponseEntity.accepted().body(scriptService.ingest(request));
    }
}
