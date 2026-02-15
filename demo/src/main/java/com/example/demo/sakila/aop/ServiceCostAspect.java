package com.example.demo.sakila.aop;

import java.util.concurrent.TimeUnit;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class ServiceCostAspect {
    private static final Logger SERVICE_COST_LOG = LoggerFactory.getLogger("SERVICE_COST");
    private final long slowThresholdMs;

    public ServiceCostAspect(@Value("${app.aop.service-slow-threshold-ms:300}") long slowThresholdMs) {
        this.slowThresholdMs = slowThresholdMs;

    }

    @Around("execution(* com.example.demo.sakila.service.*.*(..))")
    public Object aroundService(ProceedingJoinPoint joinPoint) throws Throwable {
        long startNs = System.nanoTime();
        try {
            return joinPoint.proceed();
        } finally {
            long costMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNs);
            String method = joinPoint.getSignature().toShortString();
            if (costMs >= slowThresholdMs) {
                SERVICE_COST_LOG.warn("[SLOW_SERVICE] method={},cost={}ms", method, costMs);
            } else {
                SERVICE_COST_LOG.info("[SERVICE] method={},cost={}ms", method, costMs);
            }
        }
    }
}
