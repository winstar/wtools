package com.eveow.wtools.redis.test;

import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.Map;

/**
 * @author wangjianping
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = Application.class)
public class StringTemplateTest {

    @Autowired
    private RedisConnectionFactory factory;

    @Test
    public void pushTest() {
        StringRedisTemplate redisTemplate = new StringRedisTemplate(factory);

        Map<Object, Object> map = redisTemplate.opsForHash().entries("hello:map");
        if (map == null) {
            System.out.println("map null");
        } else if (map.isEmpty()) {
            System.out.println("map empty");
        } else {
            map.entrySet().forEach(e -> System.out.println(e.getValue().getClass().getSimpleName() + e.getKey() + ": " + e.getValue()));
        }
    }
}
