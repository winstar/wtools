package com.eveow.wtools.redis.common;

import org.springframework.data.redis.connection.RedisConnectionFactory;
import java.util.concurrent.TimeUnit;


/**
 *
 *
 * @author wangjianping
 */
public class RedisRateLimiter {

    /**
     * spring redis的连接池
     */
    private RedisConnectionFactory factory;

    private String name;

    private double rate;

    private boolean single;

    private RateLimiter innerLimiter;

    public RedisRateLimiter(RedisConnectionFactory factory, String name, double rate) {
        this(factory, name, rate, false);
    }

    public RedisRateLimiter(RedisConnectionFactory factory, String name, double rate, boolean single) {
        this.innerLimiter = RateLimiter.create(rate);
        this.factory = factory;
        this.name = name;
        this.rate = rate;
        this.single = single;
    }

    /**
     * 动态调整流速（每秒生成令牌数）
     */
    public final void setRate(double permitsPerSecond) {
        if (permitsPerSecond <= 0.0 || Double.isNaN(permitsPerSecond)) {
            throw new IllegalArgumentException("rate must be positive");
        }

    }

    /**
     * 获取流速
     */
    public final double getRate() {
        return rate;
    }

    /**
     * 获取一个令牌
     */
    public double acquire() {
        return acquire(1);
    }

    /**
     * 获取permits个令牌
     */
    public double acquire(int permits) {
        return innerLimiter.acquire(permits);
    }

    /**
     * 尝试获取一个令牌
     */
    public boolean tryAcquire() {
        return tryAcquire(1, 0, TimeUnit.MICROSECONDS);
    }

    /**
     * 尝试获取permits个令牌
     */
    public boolean tryAcquire(int permits) {
        return tryAcquire(permits, 0, TimeUnit.MICROSECONDS);
    }

    /**
     * 在超时时间内尝试获取1个令牌
     */
    public boolean tryAcquire(long timeout, TimeUnit unit) {
        return tryAcquire(1, timeout, unit);
    }

    /**
     * 在超时时间内尝试获取permits个令牌
     */
    public boolean tryAcquire(int permits, long timeout, TimeUnit unit) {
        return innerLimiter.tryAcquire(permits, timeout, unit);
    }
}
