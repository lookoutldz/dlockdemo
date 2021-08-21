package com.example.dlockdemo.util;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.data.redis.core.BoundValueOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Component
public class RedisUtils {

    public RedisTemplate<String, String> getRedisTemplate() {
        return this.redisTemplate;
    }

    private final RedisTemplate<String, String> redisTemplate;

    private final BoundValueOperations<String, String> lockOps;

    private static final String LOCK = "lock";

    private final ThreadLocal<lockEntry> threadLocal =
            ThreadLocal.withInitial(() -> new lockEntry(UUID.randomUUID().toString(), 1));

    public RedisUtils(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.lockOps = redisTemplate.boundValueOps(LOCK);
    }

    public boolean tryLock() {
        // 加锁 设置超时
        boolean locked = Boolean.TRUE == lockOps.setIfAbsent(threadLocal.get().uuid, 15, TimeUnit.SECONDS);
        // 可重入
        if (!locked && threadLocal.get().uuid.equals(lockOps.get())) {
            threadLocal.get().count++;
        }
        return locked;
    }

    public void watchDog() {
        System.out.println("watching...");
        new Thread(() -> {
            try {
                Thread.sleep(10000);
                lockOps.expire(10, TimeUnit.SECONDS);
                System.out.println("+10s");
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }).start();
    }

    public void unlock() {
        if (!threadLocal.get().uuid.equals(lockOps.get())) {
            return;
        }
        // 可重入
        if (threadLocal.get().count > 0) {
            threadLocal.get().count--;
        }
        // 释放锁
        if (threadLocal.get().count == 0L) {
            redisTemplate.delete(LOCK);
        }
    }

    @Data
    @AllArgsConstructor
    static class lockEntry {
        private String uuid;
        private Integer count;
    }
}
