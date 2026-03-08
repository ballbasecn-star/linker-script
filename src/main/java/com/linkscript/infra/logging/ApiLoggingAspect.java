package com.linkscript.infra.logging;

import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.util.StopWatch;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Aspect
@Component
public class ApiLoggingAspect {

    private static final Logger log = LoggerFactory.getLogger(ApiLoggingAspect.class);

    private final LinkScriptLoggingProperties properties;
    private final ApiLogSanitizer sanitizer;

    public ApiLoggingAspect(LinkScriptLoggingProperties properties, ApiLogSanitizer sanitizer) {
        this.properties = properties;
        this.sanitizer = sanitizer;
    }

    @Around("within(com.linkscript.api..*)")
    public Object logApiCall(ProceedingJoinPoint joinPoint) throws Throwable {
        if (!properties.enabled()) {
            return joinPoint.proceed();
        }

        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        StopWatch stopWatch = new StopWatch();
        stopWatch.start();

        String requestId = MDC.get(RequestTraceFilter.REQUEST_ID);
        String route = currentRoute();
        String method = currentMethod();
        String arguments = formatArguments(signature.getParameterNames(), joinPoint.getArgs());

        log.info("api.request requestId={} method={} route={} handler={} args={}",
                requestId,
                method,
                route,
                signature.toShortString(),
                arguments
        );

        try {
            Object result = joinPoint.proceed();
            stopWatch.stop();
            log.info("api.response requestId={} method={} route={} handler={} durationMs={} result={}",
                    requestId,
                    method,
                    route,
                    signature.toShortString(),
                    stopWatch.getTotalTimeMillis(),
                    sanitizer.sanitize(result)
            );
            return result;
        } catch (Throwable throwable) {
            stopWatch.stop();
            log.error("api.error requestId={} method={} route={} handler={} durationMs={} message={}",
                    requestId,
                    method,
                    route,
                    signature.toShortString(),
                    stopWatch.getTotalTimeMillis(),
                    throwable.getMessage(),
                    throwable
            );
            throw throwable;
        }
    }

    private String formatArguments(String[] parameterNames, Object[] args) {
        Map<String, Object> payload = new LinkedHashMap<>();
        for (int index = 0; index < args.length; index++) {
            Object arg = args[index];
            if (arg instanceof ServletRequest || arg instanceof ServletResponse) {
                continue;
            }
            String key = parameterNames != null && index < parameterNames.length ? parameterNames[index] : "arg" + index;
            payload.put(key, arg);
        }
        return sanitizer.sanitize(payload);
    }

    private String currentMethod() {
        ServletRequestAttributes attributes = currentAttributes();
        return attributes == null ? "-" : attributes.getRequest().getMethod();
    }

    private String currentRoute() {
        ServletRequestAttributes attributes = currentAttributes();
        if (attributes == null) {
            return "-";
        }
        String query = attributes.getRequest().getQueryString();
        String uri = attributes.getRequest().getRequestURI();
        return query == null ? uri : uri + "?" + query;
    }

    private ServletRequestAttributes currentAttributes() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servletRequestAttributes) {
            return servletRequestAttributes;
        }
        return null;
    }
}
