package com.codejam.execution.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
@ConditionalOnProperty(name = "app.executor.type", havingValue = "judge0")
public class Judge0RateLimiter {

    private static final int DAILY_LIMIT = 50;
    private static final int WARNING_THRESHOLD = 40;
    private static final int BUFFER = 5; // Keep 5 calls as buffer
    private static final int MAX_ALLOWED = DAILY_LIMIT - BUFFER; // 45 calls max

    private final AtomicInteger dailyCallCount = new AtomicInteger(0);
    private volatile LocalDate currentDate;

    public Judge0RateLimiter() {
        this.currentDate = LocalDate.now(ZoneOffset.UTC);
    }

    /**
     * Check if execution is allowed for the given user
     * @param userId User identifier (can be roomId or any identifier)
     * @return true if execution is allowed, false if rate limit exceeded
     */
    public boolean allowExecution(String userId) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        
        // Reset counter if date changed
        if (!today.equals(currentDate)) {
            synchronized (this) {
                if (!today.equals(currentDate)) {
                    int previousCount = dailyCallCount.get();
                    dailyCallCount.set(0);
                    currentDate = today;
                    log.info("Rate limiter reset for new day. Previous count: {}", previousCount);
                }
            }
        }

        int currentCount = dailyCallCount.get();
        
        // Log warning if approaching limit
        if (currentCount >= WARNING_THRESHOLD && currentCount < MAX_ALLOWED) {
            log.warn("Judge0 API approaching daily limit: {}/{} calls used", currentCount, DAILY_LIMIT);
        }
        
        // Check if limit exceeded
        if (currentCount >= MAX_ALLOWED) {
            log.warn("Judge0 API daily limit reached: {}/{} calls. Execution denied for user: {}", 
                    currentCount, DAILY_LIMIT, userId);
            return false;
        }
        
        // Increment and allow
        int newCount = dailyCallCount.incrementAndGet();
        log.debug("Judge0 API call count: {}/{} for user: {}", newCount, DAILY_LIMIT, userId);
        return true;
    }

    /**
     * Get current daily call count
     */
    public int getCurrentCount() {
        return dailyCallCount.get();
    }

    /**
     * Reset counter at midnight UTC (scheduled task)
     */
    @Scheduled(cron = "0 0 0 * * *", zone = "UTC")
    public void resetDailyCounter() {
        int previousCount = dailyCallCount.getAndSet(0);
        currentDate = LocalDate.now(ZoneOffset.UTC);
        log.info("Rate limiter reset at midnight UTC. Previous count: {}", previousCount);
    }
}
