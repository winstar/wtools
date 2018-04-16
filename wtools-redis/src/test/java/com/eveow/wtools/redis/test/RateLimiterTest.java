package com.eveow.wtools.redis.test;

import com.eveow.wtools.redis.common.RateLimiter;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

/**
 * @author wangjianping
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = Application.class)
public class RateLimiterTest {

    @Test
    public void commonTest() {
        RateLimiter limiter = RateLimiter.create(1000);

        for (int i=0;i<50000;i++) {
            limiter.acquire();
            if (i%100 == 0) {
                System.out.println(i);
            }
        }
    }

    @Test
    public void limiterTest() {
        RateLimiter limiter = RateLimiter.create(13);

        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        for (int i = 0; i < 3; i++) {
            new Thread(() -> {
                for (int j = 0; j < 100; j++) {
                    System.out.println(limiter.tryAcquire());
                    try {
                        Thread.sleep(200);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }).start();
        }

        try {
            Thread.sleep(60000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
