package com.eveow.wtools.redis;

import com.eveow.wtools.redis.lock.RedisLock;
import org.springframework.data.redis.connection.RedisConnectionFactory;

/**
 * @author wangjianping
 */
public class RedisTools {

    /**
     * spring redis的连接池
     */
    private RedisConnectionFactory factory;

    public RedisTools(RedisConnectionFactory factory) {
        this.factory = factory;
    }

    /**
     * 获取分布式锁
     * 
     * @param name
     * @return
     */
    public RedisLock getLock(String name) {
        return new RedisLock(factory, name);
    }
}
