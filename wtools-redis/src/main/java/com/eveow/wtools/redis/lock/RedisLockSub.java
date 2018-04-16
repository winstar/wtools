package com.eveow.wtools.redis.lock;

import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author wangjianping
 */
public class RedisLockSub {

    /**
     * 订阅线程记录
     */
    private final Map<Long, Optional<String>> threadMap = new ConcurrentHashMap<>();

    /**
     * 通道名称
     */
    private String channel = "default";

    /**
     * 最大连接时间
     */
    private long maxConnectTime = 30000;

    /**
     * 连接池
     */
    private RedisConnectionFactory factory;

    /**
     * 当前连接
     */
    private RedisConnection connection;

    /**
     * 连接标记
     */
    private AtomicBoolean connected = new AtomicBoolean(false);

    /**
     * 定时检测
     */
    private AtomicBoolean scheduled = new AtomicBoolean(false);

    /**
     * 订阅线程池
     */
    private ExecutorService executor = Executors.newFixedThreadPool(1);

    /**
     * 调度线城池
     */
    private ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    public RedisLockSub(RedisConnectionFactory factory, String channel) {
        this.factory = factory;
        this.channel = channel;
    }

    /**
     * 初始化监听器
     */
    private final MessageListener listener = (message, pattern) -> {
        // 对各线程分别赋值
        if (!threadMap.isEmpty()) {
            Optional<String> subVal = Optional.of(new String(message.getBody()));
            threadMap.entrySet().forEach(e -> e.setValue(subVal));
        }
        synchronized (this) {
            this.notifyAll();
        }
    };

    /**
     * 订阅
     */
    public synchronized void subscribe() {
        if (!connected.get()) {
            connection = factory.getConnection();
            if (connection.isSubscribed()) {
                throw new RuntimeException("connection is subscribed");
            }
            // 开线程监听订阅通道
            final RedisConnection curConn = connection;
            executor.execute(() -> {
                // 阻塞线程，监听订阅的通道消息
                curConn.subscribe(listener, channel.getBytes());
            });
            connected.set(true);
        }
        // 开启定时检查
        if (scheduled.compareAndSet(false, true)) {
            scheduler.scheduleAtFixedRate(() -> {
                // 异常连接
                if (connection != null && connection.isClosed()) {
                    connection = null;
                    connected.set(false);
                }
                // 释放连接
                if (threadMap.isEmpty() && connected.get()) {
                    release();
                }
            }, maxConnectTime, maxConnectTime, TimeUnit.MILLISECONDS);
        }
        // 注册当前线程
        long threadId = Thread.currentThread().getId();
        if (!threadMap.containsKey(threadId)) {
            threadMap.put(threadId, Optional.empty());
        }
    }

    /**
     * 监听订阅消息
     */
    public String listen() throws InterruptedException {
        return listen(maxConnectTime);
    }

    /**
     * 监听订阅消息
     *
     * @param timeout 阻塞等待时间
     */
    public synchronized String listen(long timeout) throws InterruptedException {
        // 当前线程
        long threadId = Thread.currentThread().getId();
        try {
            this.wait(timeout);
            Optional<String> opt = threadMap.get(threadId);
            return opt != null && opt.isPresent() ? opt.get() : null;
        } finally {
            // 清理订阅数据
            threadMap.put(threadId, Optional.empty());
        }
    }

    /**
     * 取消当前的订阅
     */
    public synchronized void unsubscribe() {
        // 移除当前线程
        long threadId = Thread.currentThread().getId();
        threadMap.remove(threadId);
    }

    /**
     * 释放连接
     */
    public synchronized void release() {
        if (connection != null) {
            if (connection.isSubscribed()) {
                // 触发监听线程结束
                connection.getSubscription().unsubscribe();
            }
            // 归还redis连接
            connection.close();
            connection = null;
            // 标记为未连接状态，以使下次的订阅初始化连接
            connected.set(false);
        }
    }
}
