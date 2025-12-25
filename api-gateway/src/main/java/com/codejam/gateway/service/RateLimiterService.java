package com.codejam.gateway.service;

import com.codejam.commons.service.RedisService;
import com.codejam.commons.util.proxyUtils;
import com.codejam.gateway.config.MicroserviceConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@RequiredArgsConstructor
public class RateLimiterService {

    private final RedisService redisService;
    private final proxyUtils proxyUtils;
    private final MicroserviceConfig microserviceConfig;

    public Mono<Boolean> checkRateLimit(String userId) {
        return Mono.fromCallable(() -> {
            try {
                int maxRequests = microserviceConfig.getRateLimit().getMaxRequests();
                long windowDurationSeconds = microserviceConfig.getRateLimit().getWindowDurationSeconds();

                String key = proxyUtils.generateRedisKey("RATE_LIMIT_OTP", userId);
                String currentCount = redisService.get(key);

                if (currentCount == null) {
                    redisService.set(key, "1", windowDurationSeconds);
                    return true;
                }

                int count = Integer.parseInt(currentCount);
                if (count >= maxRequests) {
                    return false;
                }

                redisService.increment(key);
                return true;
            } catch (Exception e) {
                log.error("Rate limit check failed for user: {}", userId, e);
                return true;
            }
        });
    }
}
