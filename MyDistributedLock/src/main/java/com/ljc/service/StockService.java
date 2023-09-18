package com.ljc.service;

import com.ljc.util.RedisReentrantLock;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.locks.LockSupport;

@Service
public class StockService {
    @Resource
    private StringRedisTemplate redisTemplate;

    @Resource
    private RedisReentrantLock redisLock;

    /**
     * 用redis做分布式锁解决秒杀超卖问题
     */
    public String sell_redisDistributedLock() {
        String lockName = "lock";   // 锁key name
        String stockName = "stockCount";    // 库存key name
        String lockValue = "busiId" + UUID.randomUUID().toString(); // 业务id，用于标识当前业务

        try {
            Integer stockCount = Integer.parseInt(redisTemplate.opsForValue().get(stockName)); // 当前商品库存
            if (stockCount == 0) {  // 如果请求数量大于库存数量不算少的话，加锁前先做这个检测有利于提高整体tps
                return "已卖完";
            }

            // 获取锁
            while (Objects.equals(-1L, redisLock.tryLock(List.of(lockName), lockValue, "1", "10"))) {  // 新增设置锁的过期时间
                LockSupport.parkNanos(10 * 1000 * 1000);
            }

            // 获取锁后添加自动续期功能
            redisLock.autoRenew(lockName, lockValue, 10);

            stockCount = Integer.parseInt(redisTemplate.opsForValue().get(stockName));
            if (stockCount == 0) {
                return "已卖完";
            }

            // 扣减库存
            redisTemplate.opsForValue().set(stockName, String.valueOf((stockCount - 1)));
            return "购买成功";
        } finally {
            // 释放锁
            redisLock.release(List.of(lockName), lockValue);
        }
    }

    /**
     * 用redis乐观锁解决秒杀超卖问题
     * @return
     */
    public String sell_redisOptimisticLock() {
        String lockName = "stockCount";
        return redisTemplate.execute(new SessionCallback<String>() {
            @Override
            public String execute(RedisOperations operations) throws DataAccessException {
                while (true) {
                    // 1、查出库存
                    operations.watch(lockName);
                    String stockStr = operations.opsForValue().get(lockName).toString();
                    Integer stock = Integer.valueOf(stockStr);

                    // 2、检查是否还有库存
                    if (stock <= 0) {
                        return "已卖完";
                    }

                    // 3、减库存，用乐观锁来同步
                    operations.multi();
                    operations.opsForValue().set(lockName, String.valueOf(stock - 1));
                    List execResult = operations.exec();
                    if (execResult != null && execResult.size() != 0) {
                        break;
                    }
//                    LockSupport.parkNanos(10 * 1000 * 1000);
                }
                return "购买成功！";
            }
        });
    }
}
