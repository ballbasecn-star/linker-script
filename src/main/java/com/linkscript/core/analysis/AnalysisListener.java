package com.linkscript.core.analysis;

import com.linkscript.core.script.ScriptService;
import com.linkscript.infra.logging.RequestTraceFilter;
import java.util.UUID;
import com.linkscript.domain.entity.ScriptStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class AnalysisListener {

    private static final Logger log = LoggerFactory.getLogger(AnalysisListener.class);

    private final AnalysisService analysisService;
    private final ScriptService scriptService;

    public AnalysisListener(AnalysisService analysisService, ScriptService scriptService) {
        this.analysisService = analysisService;
        this.scriptService = scriptService;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(AnalysisRequestedEvent event) {
        String taskId = UUID.randomUUID().toString();
        if (event.requestId() != null) {
            MDC.put(RequestTraceFilter.REQUEST_ID, event.requestId());
        }
        MDC.put("taskId", taskId);
        try {
            log.info("async.analysis.started requestId={} taskId={} scriptUuid={}",
                    MDC.get(RequestTraceFilter.REQUEST_ID),
                    taskId,
                    event.scriptUuid()
            );
            scriptService.markStatus(event.scriptUuid(), ScriptStatus.ANALYZING);
            analysisService.analyzeScript(event.scriptUuid());
            scriptService.markStatus(event.scriptUuid(), ScriptStatus.COMPLETED);
            log.info("async.analysis.completed requestId={} taskId={} scriptUuid={}",
                    MDC.get(RequestTraceFilter.REQUEST_ID),
                    taskId,
                    event.scriptUuid()
            );
        } catch (Exception exception) {
            log.error("async.analysis.failed requestId={} taskId={} scriptUuid={} message={}",
                    MDC.get(RequestTraceFilter.REQUEST_ID),
                    taskId,
                    event.scriptUuid(),
                    exception.getMessage(),
                    exception
            );
            scriptService.markStatus(event.scriptUuid(), ScriptStatus.FAILED);
        } finally {
            MDC.remove("taskId");
            MDC.remove(RequestTraceFilter.REQUEST_ID);
        }
    }
}
