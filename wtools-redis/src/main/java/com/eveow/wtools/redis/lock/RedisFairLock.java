package com.eveow.wtools.redis.lock;

import org.springframework.data.redis.connection.RedisConnectionFactory;

/**
 * @author wangjianping
 */
public class RedisFairLock extends RedisLock {

    public RedisFairLock(RedisConnectionFactory factory, String name) {
        super(factory, name);
    }
}
