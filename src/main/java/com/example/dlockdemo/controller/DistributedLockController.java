package com.example.dlockdemo.controller;

import com.example.dlockdemo.util.RedisUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.BoundValueOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@SuppressWarnings("ConstantConditions")
@RestController("/")
public class DistributedLockController {

    private final BoundValueOperations<String, String> stockOps;

    private final RedisUtils redisUtils;

    public DistributedLockController(RedisTemplate<String, String> redisTemplate, RedisUtils redisUtils) {
        this.stockOps = redisTemplate.boundValueOps("stock");
        this.redisUtils = redisUtils;
    }

    /**
     * 1. 单机原子性： synchronized
     * 2. 集群原子性： redis 变量（分布式锁）
     * 3. 误调用预防： UUID, API 闭环
     * 4. 可重入性： count
     * 4. 死锁预防： finally, expire
     * 5. 提前释放预防： 定时续命（看门狗）
     * @param consumerId 买家ID
     * @return Object
     */
    @PostMapping("/consumer/{consumerId}/product/36")
    public Object submitOrder(@PathVariable("consumerId") String consumerId) {
        try {
            // 获取锁
            if (redisUtils.tryLock()) {
                redisUtils.watchDog();
                // 拿
                long stock = Long.parseLong(stockOps.get());
                // 改
                if (stock > 0) {
                    stock--;
                }
                // 存
                stockOps.set(String.valueOf(stock));

                return "顾客[" + consumerId + "]抢到了商品";
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            // 释放锁
            redisUtils.unlock();
        }

        return "顾客[" + consumerId + "]没抢到商品";
    }

    @GetMapping("/test/")
    public Object setIfAbsent() {
        Boolean a = redisUtils.getRedisTemplate().opsForHash().putIfAbsent("test", UUID.randomUUID().toString(), "1");
        Boolean b = redisUtils.getRedisTemplate().opsForHash().putIfAbsent("test", UUID.randomUUID().toString(), "1");
        Boolean c = redisUtils.getRedisTemplate().opsForHash().putIfAbsent("test", UUID.randomUUID().toString(), "1");
        return List.of(a, b, c);
    }
}
