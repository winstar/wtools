

### ConsistentHash

一致性hash实现 [源码链接](https://github.com/winstar/wtools/blob/master/wtools-redis/src/main/java/com/eveow/wtools/redis/common/RateLimiter.java)
 
### RateLimiter

简化了下Guava RateLimiter, WarmingUp不怎么用就去掉了 [源码链接](https://github.com/winstar/wtools/blob/master/wtools-redis/src/main/java/com/eveow/wtools/redis/lock/RedisLock.java)

### RedisLock

参考了redisson的分布式锁实现，基于spring-data-redis, 使用pub/sub做等待锁的实时唤醒 [源码链接](https://github.com/winstar/wtools/blob/master/wtools-util/src/main/java/com/eveow/wtools/util/hash/ConsistentHash.java)



