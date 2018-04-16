package com.eveow.wtools.redis.test;

import com.eveow.wtools.redis.RedisTools;
import com.eveow.wtools.redis.lock.RedisLock;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * @author wangjianping
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = Application.class)
public class RedisLockTest {

    @Autowired
    private RedisConnectionFactory factory;

    @Test
    public void tryLockTest() {
        RedisTools tools = new RedisTools(factory);
        final RedisLock lock = tools.getLock("yan-lock");

        for (int i = 0; i < 10; i++) {
            final int index = i;
            new Thread(() -> {
                try {
                    if (lock.tryLock(1, TimeUnit.SECONDS)) {
                        try {
                            try {
                                Thread.sleep(100);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        } finally {
                            lock.unlock();
                            System.out.println(index);
                        }
                    } else {
                        System.out.println("try lock timeout");
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }).start();
        }

        try {
            Thread.sleep(120000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void lockToolTest() {
        RedisTools tools = new RedisTools(factory);
        final RedisLock lock = tools.getLock("yan-lock");

        ExecutorService executorService = Executors.newFixedThreadPool(10);
        for (int i = 0; i < 100; i++) {
            final int index = i;
            executorService.execute(() -> {
                lock.lock();
                try {
                    lock.lock();
                    try {
                        try {
                            Thread.sleep(100);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    } finally {
                        lock.unlock();
                    }
                } finally {
                    lock.unlock();
                    System.out.println(index);
                }
            });
        }

        try {
            Thread.sleep(120000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void lockTest() {
        RedisLock lock = new RedisLock(factory, "yan-lock");

        //System.out.println(lock.tryAcquire(-1, TimeUnit.MILLISECONDS, Thread.currentThread().getId()));

        CountDownLatch latch = new CountDownLatch(5);
        for (int i = 0; i < 5; i++) {
            final int index = i;
            new Thread(() -> {
                //System.out.println(lock.tryAcquire(60000, TimeUnit.MILLISECONDS, Thread.currentThread().getId()));
                lock.lock();
                System.out.println("lock index" + index);
                try {
                    try {
                        Thread.sleep(3000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                } finally {
                    lock.unlock();
                    latch.countDown();
                }
            }).start();
        }
        try {
            latch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
