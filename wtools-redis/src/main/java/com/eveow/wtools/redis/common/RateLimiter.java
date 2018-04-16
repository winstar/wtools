package com.eveow.wtools.redis.common;

import java.util.concurrent.TimeUnit;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

/**
 * 限流工具类，从guava的RateLimiter中提炼，只保留了Bursty实现，去掉了WarmingUp代码
 *
 * @author wangjianping
 */
public class RateLimiter {

    /**
     * 对象创建时的时间，用于做时间偏差基量
     */
    private final long offsetNanos;

    /**
     * 桶中存储的令牌书
     */
    private double storedPermits;

    /**
     * 桶的最大容量
     */
    private double maxPermits;

    /**
     * 稳定生成一张令牌的时间
     */
    private volatile double stableIntervalMicros;

    /**
     * 互斥对象
     */
    private final Object mutex = new Object();

    /**
     * 下一次可直接获取令牌的时间，可表示过去和将来；<br/>
     * 表示过去是，说明桶中还有令牌数，而表示将来，说明上一次请求提前预先消费了令牌，下一次请求需要有所等待
     */
    private long nextFreeTicketMicros = 0L;

    /**
     * 应对突发流量的时间，默认为1s，maxPermits = maxBurstSeconds * permitsPerSecond <br/>
     * 使用时可根据实际情况调整，直接影响令牌桶大小
     */
    private double maxBurstSeconds = 1.0;

    /**
     * 构造函数
     * 
     * @param permitsPerSecond 流速
     */
    public static RateLimiter create(double permitsPerSecond) {
        RateLimiter rateLimiter = new RateLimiter();
        rateLimiter.setRate(permitsPerSecond);
        return rateLimiter;
    }

    /**
     * 可指定允许的突发时间
     *
     * @param permitsPerSecond 流速
     * @param maxBurstBuildup 应对突发流量的时间，默认为1s，maxPermits = maxBurstSeconds * permitsPerSecond
     * @param unit maxBurstBuildup时间单位
     */
    public static RateLimiter create(double permitsPerSecond, long maxBurstBuildup, TimeUnit unit) {
        double maxBurstSeconds = unit.toNanos(maxBurstBuildup) / 1E+9;
        RateLimiter rateLimiter = new RateLimiter(maxBurstSeconds);
        rateLimiter.setRate(permitsPerSecond);
        return rateLimiter;
    }

    private RateLimiter() {
        this.offsetNanos = System.nanoTime();
    }

    private RateLimiter(double maxBurstSeconds) {
        this.offsetNanos = System.nanoTime();
        this.maxBurstSeconds = maxBurstSeconds;
    }

    /**
     * 动态调整流速（每秒生成令牌数）
     */
    public final void setRate(double permitsPerSecond) {
        if (permitsPerSecond <= 0.0 || Double.isNaN(permitsPerSecond)) {
            throw new IllegalArgumentException("rate must be positive");
        }
        synchronized (mutex) {
            resync(readSafeMicros());
            this.stableIntervalMicros = TimeUnit.SECONDS.toMicros(1L) / permitsPerSecond;

            // 修改流速时，按比例调整存储令牌数
            double oldMaxPermits = this.maxPermits;
            maxPermits = maxBurstSeconds * permitsPerSecond;
            storedPermits = (oldMaxPermits == 0.0) ? 0.0
                : storedPermits * maxPermits / oldMaxPermits;
        }
    }

    /**
     * 获取流速
     */
    public final double getRate() {
        return TimeUnit.SECONDS.toMicros(1L) / stableIntervalMicros;
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
        if (permits <= 0) {
            throw new IllegalArgumentException("Requested permits must be positive");
        }
        long microsToWait;
        synchronized (mutex) {
            // 成功获取令牌需要等待的时间
            microsToWait = reserveNextTicket(permits, readSafeMicros());
        }
        // 阻塞等待
        sleepMicrosUninterruptibly(microsToWait);
        return 1.0 * microsToWait / TimeUnit.SECONDS.toMicros(1L);
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
        long timeoutMicros = unit.toMicros(timeout);
        if (permits <= 0) {
            throw new IllegalArgumentException("Requested permits must be positive");
        }
        long microsToWait;
        synchronized (mutex) {
            // 获取当前的时间偏差
            long nowMicros = readSafeMicros();
            // 在超时时间内不会到达下一次时间，也就不会有新的令牌生成
            if (nextFreeTicketMicros > nowMicros + timeoutMicros) {
                return false;
            } else {
                // 成功获取令牌需要等待的时间
                microsToWait = reserveNextTicket(permits, nowMicros);
            }
        }
        // 阻塞等待
        sleepMicrosUninterruptibly(microsToWait);
        return true;
    }

    /**
     * 获取需要等待的时间，并更新桶模拟计数
     * 
     * @param requiredPermits 需要的令牌数
     * @param nowMicros 当前时间偏差
     */
    private long reserveNextTicket(double requiredPermits, long nowMicros) {
        resync(nowMicros);
        // 如果是过去时间，因为上面刚同步过，肯定为0，不需要等待；主要针对下一次是未来时间
        long microsToNextFreeTicket = nextFreeTicketMicros - nowMicros;
        // 存储的令牌有多少被使用
        double storedPermitsToSpend = Math.min(requiredPermits, this.storedPermits);
        // 需要等待新生成的令牌数（这里的等待其实是再还上一次预支的令牌，本次的预支不需要等待，留给一次再还）
        double freshPermits = requiredPermits - storedPermitsToSpend;

        // 以下函数原guava的实现里计算等待会加上，但只针对WarmingUp使用
        // storedPermitsToWaitTime(this.storedPermits, storedPermitsToSpend)
        long waitMicros = (long) (freshPermits * stableIntervalMicros);
        // 更新下一次不需要等待时间
        this.nextFreeTicketMicros = nextFreeTicketMicros + waitMicros;
        // 减扣消费的令牌数
        this.storedPermits -= storedPermitsToSpend;
        return microsToNextFreeTicket;
    }

    /**
     * 同步更新存储的令牌数
     */
    private void resync(long nowMicros) {
        if (nowMicros > nextFreeTicketMicros) {
            // 存储令牌数不能大于最大容量
            storedPermits = Math.min(maxPermits,
                storedPermits + (nowMicros - nextFreeTicketMicros) / stableIntervalMicros);
            // nextFreeTicketMicros为过去时间，更新为当前时间
            nextFreeTicketMicros = nowMicros;
        }
    }

    /**
     * 获取当前时间偏差
     */
    private long readSafeMicros() {
        return TimeUnit.NANOSECONDS.toMicros(System.nanoTime() - offsetNanos);
    }

    /**
     * 阻塞等待实现
     */
    private void sleepMicrosUninterruptibly(long micros) {
        if (micros > 0) {
            boolean interrupted = false;
            try {
                long remainingNanos = TimeUnit.MICROSECONDS.toNanos(micros);
                long end = System.nanoTime() + remainingNanos;
                while (true) {
                    try {
                        // TimeUnit.sleep() treats negative timeouts just like zero.
                        NANOSECONDS.sleep(remainingNanos);
                        return;
                    } catch (InterruptedException e) {
                        interrupted = true;
                        remainingNanos = end - System.nanoTime();
                    }
                }
            } finally {
                if (interrupted) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }
}
