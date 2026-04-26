package com.yuvvsi.ppe.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Slf4j
@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private final RedisTemplate<String, String> redisTemplate;

    private static final String KEY_PREFIX = "idempotency:";
    private static final Duration TTL = Duration.ofHours(24);

    public boolean isDuplicate(String idempotencyKey) {
        String redisKey = KEY_PREFIX + idempotencyKey;
        Boolean exists = redisTemplate.hasKey(redisKey);
        log.info("Idempotency check for key {}: duplicate={}", idempotencyKey, exists);
        return Boolean.TRUE.equals(exists);
    }

    public void store(String idempotencyKey, String transactionId) {
        String redisKey = KEY_PREFIX + idempotencyKey;
        redisTemplate.opsForValue().set(redisKey, transactionId, TTL);
        log.info("Stored idempotency key {} -> transactionId {}", idempotencyKey, transactionId);
    }

    public String getTransactionId(String idempotencyKey) {
        String redisKey = KEY_PREFIX + idempotencyKey;
        return redisTemplate.opsForValue().get(redisKey);
    }
}