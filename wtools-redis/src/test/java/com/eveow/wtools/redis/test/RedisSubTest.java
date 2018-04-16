package com.eveow.wtools.redis.test;

import com.eveow.wtools.redis.lock.RedisLockSub;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * @author wangjianping
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = Application.class)
public class RedisSubTest {

    @Autowired
    private RedisConnectionFactory factory;

    @Test
    public void pushTest() {
        RedisConnection connection = factory.getConnection();
        int i = 0;
        while (true) {
            connection.publish("yan-channel".getBytes(), "1".getBytes());
            i++;
            if (i % 10000 == 0) {
                System.out.println(i);
            }
        }
    }

    @Test
    public void reconnectTest() throws Exception {
        RedisLockSub sub = new RedisLockSub(factory, "yan-channel");

        for (int i = 0; i < 10; i++) {
            sub.subscribe();
            try {
                System.out.println("listening");
                System.out.println(sub.listen());
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                sub.unsubscribe();
            }

            Thread.sleep(30000);
        }
    }

    @Test
    public void baseTest() throws Exception {

        RedisLockSub sub = new RedisLockSub(factory, "yan-channel");

        ExecutorService executorService = Executors.newFixedThreadPool(100);

        long time = System.currentTimeMillis();
        CountDownLatch latch = new CountDownLatch(10);
        for (int i = 0; i < 10; i++) {
            executorService.execute(() -> {
                sub.subscribe();
                try {
                    sub.listen();
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    sub.unsubscribe();
                    latch.countDown();
                }
            });
        }
        System.out.println("countDown:" + latch.getCount());
        latch.await();
        System.out.println("countDown:" + latch.getCount());

        long endtime = System.currentTimeMillis();
        System.out.println(endtime - time);

        System.out.println("finish");
        sub.subscribe();
        System.out.println(sub.listen());
        sub.unsubscribe();

        try {
            Thread.sleep(40000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        System.exit(0);

        Runnable runnable = () -> {
            //long id = Thread.currentThread().getId();
            sub.subscribe();
            try {
                System.out.println(":" + sub.listen(5000));
                System.out.println(">" + sub.listen());
                System.out.println(":" + sub.listen(5000));
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                sub.unsubscribe();
            }
        };

        for (int i = 0; i < 200; i++) {
            new Thread(runnable).start();
        }

        try {
            Thread.sleep(30000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        for (int i = 0; i < 2; i++) {
            //new Thread(runnable).start();
        }
        try {
            Thread.sleep(120000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
