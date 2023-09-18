package com.ljc.util;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.List;
import java.util.Objects;
import java.util.Timer;
import java.util.TimerTask;

@Component
public class RedisReentrantLock {

    @Resource
    private StringRedisTemplate redisTemplate;

    /**
     * try加锁
     * @param keys
     * @param values
     * @return
     */
    public Long tryLock(List<String> keys, Object... values) {
        /*
            lua脚本：
            -- KEYS[1]为锁的键名；ARGV[1]为当前上锁的业务标识，可以用线程id，也可用唯一标识业务的业务id；
            -- ARGV[2]为当前上了几次锁，用于实现可重入的功能；ARGV[3]为锁的过期时间，用于防止业务非正常退出导致死锁.
            if redis.call('exists', KEYS[1]) == 0 -- 如果锁不存在
            then
                redis.call('hset', KEYS[1], ARGV[1], ARGV[2]) -- 新增锁
                redis.call('expire', KEYS[1], ARGV[3]) -- 给锁设置过期时间
                return 0
            elseif redis.call('hexists', KEYS[1], ARGV[1]) == 1 -- 若锁已存在，且是当前业务持有锁
            then
            	redis.call('hincrby', KEYS[1], ARGV[1], 1) -- 当前加锁次数加1
            	return 0
            else -- 锁存在且并非当前业务持有锁
            	return -1
            end
        */
        String lockScript = "if redis.call('exists', KEYS[1]) == 0 then redis.call('hset', KEYS[1], ARGV[1], ARGV[2]) redis.call('expire', KEYS[1], ARGV[3]) return 0 elseif redis.call('hexists', KEYS[1], ARGV[1]) == 1 then redis.call('hincrby', KEYS[1], ARGV[1], 1) return 0 else return -1 end";
        return redisTemplate.execute(new DefaultRedisScript<>(lockScript, Long.class),
                keys, values); // 这里如果用Integer.class会报错
    }

    public void release(List<String> keys, Object... values) {
        /*
            lua脚本：
            -- KEYS[1]为锁的键名；ARGV[1]为当前上锁的业务标识，可以用线程id，也可用唯一标识业务的业务id；
            -- ARGV[2]为当前上了几次锁，用于实现可重入的功能；ARGV[3]为锁的过期时间，用于防止业务非正常退出导致死锁.
            if redis.call('hexists', KEYS[1], ARGV[1]) ~= 0 	-- 若当前是业务持有的锁
            then
                if redis.call('hincrby', KEYS[1], ARGV[1], -1) <= 0 	-- 则把加锁的数量减1，并判断加锁数是否已经为0
            	then
            	    redis.call('hdel', KEYS[1], ARGV[1]) -- 如果加锁数量为0则删锁
            	end
            end
        */
        String unlockScripts = "if redis.call('hexists', KEYS[1], ARGV[1]) ~= 0 then if redis.call('hincrby', KEYS[1], ARGV[1], -1) <= 0 then redis.call('hdel', KEYS[1], ARGV[1]) end end";
        redisTemplate.execute(new DefaultRedisScript<>(unlockScripts, Boolean.class), keys, values);
    }

    /**
     * 自动续期
     * @param lockName
     * @param lockValue
     * @param ttl
     */
    public void autoRenew(String lockName, String lockValue, Integer ttl) {
        /*
            lua脚本：
            -- KEYS[1]为锁的键名；ARGV[1]为当前上锁的业务标识，可以用线程id，也可用唯一标识业务的业务id；
            -- ARGV[2]为锁的过期时间，用于防止业务非正常退出导致死锁.
            if redis.call('hexists', KEYS[1], ARGV[1]) ~= 0 	-- 若是当前业务持有锁
            then
                redis.call('expire', KEYS[1], ARGV[2]) 	-- 续期
                return 1
            else
                return 0
            end
        */
        String scripts = "if redis.call('hexists', KEYS[1], ARGV[1]) ~= 0 then redis.call('expire', KEYS[1], ARGV[2]) return 1 else return 0 end";
        Timer timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                if (Objects.equals(redisTemplate.execute(new DefaultRedisScript<>(scripts, Long.class),
                        List.of(lockName), lockValue, String.valueOf(ttl)), 0L)) {
                    System.out.println("end!");
                    cancel();
                }
                System.out.println("continue!");
            }
        }, 100, 9000);
    }
}
