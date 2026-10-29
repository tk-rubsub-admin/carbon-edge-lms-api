package com.carbonedge.backend.service;

import com.carbonedge.backend.exception.ConflictException;
import com.carbonedge.backend.exception.ForbiddenException;
import com.carbonedge.backend.exception.NotFoundException;
import com.carbonedge.backend.exception.TooManyRequestsException;
import com.carbonedge.backend.exception.UnauthorizedException;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

/**
 * Adds consistent duration and failure logs to public service calls without logging arguments,
 * which may contain credentials or personal information.
 */
@Aspect
@Component
@Slf4j
public class ServiceLoggingAspect {

    @Around("execution(public * com.carbonedge.backend.service..*(..))")
    public Object logServiceCall(ProceedingJoinPoint joinPoint) throws Throwable {
        long startedAt = System.nanoTime();
        String method = joinPoint.getSignature().toShortString();
        try {
            Object result = joinPoint.proceed();
            log.info("Service completed: method={}, durationMs={}", method, elapsedMillis(startedAt));
            return result;
        } catch (Throwable exception) {
            long durationMs = elapsedMillis(startedAt);
            if (isExpectedServiceException(exception)) {
                log.warn("Service rejected request: method={}, durationMs={}, exceptionType={}",
                        method, durationMs, exception.getClass().getSimpleName());
            } else {
                log.error("Service failed: method={}, durationMs={}, exceptionType={}",
                        method, durationMs, exception.getClass().getSimpleName(), exception);
            }
            throw exception;
        }
    }

    private long elapsedMillis(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }

    private boolean isExpectedServiceException(Throwable exception) {
        return exception instanceof ConflictException
                || exception instanceof ForbiddenException
                || exception instanceof NotFoundException
                || exception instanceof TooManyRequestsException
                || exception instanceof UnauthorizedException
                || exception instanceof IllegalArgumentException;
    }
}
