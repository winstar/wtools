package com.eveow.wtools.redis.lock;

import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.ReturnType;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;

/**
 * 基于spring-redis-data的分布式锁实现
 * 
 * @author wangjianping
 */
public class RedisLock implements Lock {

    /**
     * spring redis的连接池
     */
    private RedisConnectionFactory factory;

    /**
     * 锁名称
     */
    private String name;

    /**
     * 分布式下唯一节点标识
     */
    private final UUID id = UUID.randomUUID();

    /**
     * redis订阅对象，监控锁释放
     */
    private RedisLockSub lockSub;

    /**
     * 默认锁等待时间
     */
    private static final Long DEFAULT_INTERNAL_TIME = 30000L;

    /**
     * 加锁脚本
     * 
     * <pre>
     *  if (redis.call('exists', KEYS[1]) == 0) then
     *      redis.call('hset', KEYS[1], ARGV[2], 1); 
     *      redis.call('pexpire', KEYS[1], ARGV[1]); 
     *      return nil;
     *  end; 
     *  if (redis.call('hexists', KEYS[1], ARGV[2]) == 1) then 
     *      redis.call('hincrby', KEYS[1], ARGV[2], 1); 
     *      redis.call('pexpire', KEYS[1], ARGV[1]); 
     *      return nil; 
     *  end; 
     *  return redis.call('pttl', KEYS[1]);
     * </pre>
     */
    private static final String LOCK_SCRIPT = "if (redis.call('exists', KEYS[1]) == 0) then redis.call('hset', KEYS[1], ARGV[2], 1); redis.call('pexpire', KEYS[1], ARGV[1]); return nil; end; if (redis.call('hexists', KEYS[1], ARGV[2]) == 1) then redis.call('hincrby', KEYS[1], ARGV[2], 1); redis.call('pexpire', KEYS[1], ARGV[1]); return nil; end; return redis.call('pttl', KEYS[1]);";

    /**
     * 解锁脚本
     * 
     * <pre>
     *  if (redis.call('exists', KEYS[1]) == 0) then
     *      redis.call('publish', KEYS[2], ARGV[1]);
     *      return 1;
     *  end;
     *  if (redis.call('hexists', KEYS[1], ARGV[3]) == 0) then
     *      return nil;
     *  end;
     *  local counter = redis.call('hincrby', KEYS[1], ARGV[3], -1);
     *  if (counter > 0) then
     *      redis.call('pexpire', KEYS[1], ARGV[2]);
     *      return 0;
     *  else
     *      redis.call('del', KEYS[1]);
     *      redis.call('publish', KEYS[2], ARGV[1]);
     *      return 1;
     *  end;
     *  return nil;
     * </pre>
     */
    private static final String UNLOCK_SCRIPT = "if (redis.call('exists', KEYS[1]) == 0) then redis.call('publish', KEYS[2], ARGV[1]); return 1; end; if (redis.call('hexists', KEYS[1], ARGV[3]) == 0) then return nil; end; local counter = redis.call('hincrby', KEYS[1], ARGV[3], -1); if (counter > 0) then redis.call('pexpire', KEYS[1], ARGV[2]); return 0; else redis.call('del', KEYS[1]); redis.call('publish', KEYS[2], ARGV[1]); return 1; end; return nil;";

    private RedisLock() {}

    public RedisLock(RedisConnectionFactory factory, String name) {
        this.factory = factory;
        this.name = name;
        this.lockSub = new RedisLockSub(factory, getChannelName());
    }

    /**
     * 锁获得者，节点名+线程ID
     * 
     * @param threadId
     * @return
     */
    protected String getLockName(long threadId) {
        return id + ":" + threadId;
    }

    /**
     * 订阅通道名
     * 
     * @return
     */
    protected String getChannelName() {
        return "redis_lock_channel:" + name;
    }

    @Override
    public void lock() {
        try {
            lockInterruptibly();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 加锁
     * 
     * @param leaseTime 锁过期时间
     * @param unit
     */
    public void lock(long leaseTime, TimeUnit unit) {
        try {
            lockInterruptibly(leaseTime, unit);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void lockInterruptibly() throws InterruptedException {
        lockInterruptibly(-1, null);
    }

    /**
     * 加锁可中断
     * 
     * @param leaseTime 锁过期时间
     * @param unit
     * @throws InterruptedException
     */
    public void lockInterruptibly(long leaseTime, TimeUnit unit) throws InterruptedException {
        long threadId = Thread.currentThread().getId();
        Long ttl = tryAcquire(leaseTime, unit, threadId);
        // 获取到锁
        if (ttl == null) {
            return;
        }
        // 订阅锁通知
        lockSub.subscribe();
        try {
            while (true) {
                // 等待锁释放通知
                lockSub.listen(ttl);
                // 再次尝试获取锁
                ttl = tryAcquire(leaseTime, unit, threadId);
                // 获取到锁
                if (ttl == null) {
                    break;
                }
            }
        } finally {
            lockSub.unsubscribe();
        }
    }

    /**
     * 获取锁
     * 
     * @param leaseTime 锁过期时间
     * @param unit 时间单位
     * @param threadId 线程id
     * @return
     */
    protected Long tryAcquire(long leaseTime, TimeUnit unit, long threadId) {
        // key过期时间
        Long expireTime = DEFAULT_INTERNAL_TIME;
        if (leaseTime > 0 && unit != null) {
            expireTime = unit.toMillis(leaseTime);
        }

        RedisConnection connection = factory.getConnection();
        try {
            String lockName = getLockName(threadId);

            return connection.eval(LOCK_SCRIPT.getBytes(), ReturnType.INTEGER, 1,
                convert(name, expireTime, lockName));
        } finally {
            connection.close();
        }
    }

    @Override
    public boolean tryLock() {
        long threadId = Thread.currentThread().getId();
        return tryAcquire(-1, null, threadId) == null;
    }

    /**
     * 尝试获取锁
     * 
     * @param time 尝试等待时间
     * @param leaseTime 锁过期时间
     * @param unit
     * @return 获取锁返回true,反之超时未获取返回false
     * @throws InterruptedException
     */
    public boolean tryLock(long time, long leaseTime, TimeUnit unit) throws InterruptedException {
        long threadId = Thread.currentThread().getId();
        long current = System.currentTimeMillis();
        Long ttl = tryAcquire(leaseTime, unit, threadId);
        // 获取到锁
        if (ttl == null) {
            return true;
        }
        // 不重试
        if (time <= 0 || unit == null) {
            return false;
        }
        long waitTime = unit.toMillis(time);
        // 订阅锁通知
        lockSub.subscribe();
        try {
            while (true) {
                // 判断已到等待时间
                long leftTime = waitTime - (System.currentTimeMillis() - current);
                if (leftTime <= 0) {
                    return false;
                }
                leftTime = leftTime > ttl ? ttl : leftTime;
                // 等待锁释放通知
                lockSub.listen(leftTime);
                // 再次尝试获取锁
                ttl = tryAcquire(leaseTime, unit, threadId);
                // 获取到锁
                if (ttl == null) {
                    return true;
                }
            }
        } finally {
            lockSub.unsubscribe();
        }
    }

    @Override
    public boolean tryLock(long time, TimeUnit unit) throws InterruptedException {
        return tryLock(time, -1, unit);
    }

    @Override
    public void unlock() {
        long threadId = Thread.currentThread().getId();
        Boolean opStatus = unlockInner(threadId);
        if (opStatus == null) {
            throw new IllegalMonitorStateException(
                "attempt to unlock lock, not locked by current thread by node id: " + id
                    + " thread-id: " + threadId);
        }
    }

    protected Boolean unlockInner(long threadId) {
        RedisConnection connection = factory.getConnection();
        try {
            String lockName = getLockName(threadId);
            return connection.eval(UNLOCK_SCRIPT.getBytes(), ReturnType.BOOLEAN, 2,
                convert(name, getChannelName(), 0L, DEFAULT_INTERNAL_TIME, lockName));
        } finally {
            connection.close();
        }
    }

    protected byte[][] convert(Object... args) {
        byte[][] array = new byte[args.length][];
        for (int i = 0; i < args.length; i++) {
            Object arg = args[i];
            if (arg instanceof String) {
                array[i] = ((String) arg).getBytes();
            } else {
                array[i] = String.valueOf(arg).getBytes();
            }
        }
        return array;
    }

    @Override
    public Condition newCondition() {
        return null;
    }
}
