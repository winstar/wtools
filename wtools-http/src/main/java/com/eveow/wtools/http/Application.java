package com.eveow.wtools.http;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author wangjianping
 */
@SpringBootApplication
@ComponentScan
@EnableScheduling
@RestController
public class Application {

    @RequestMapping("/sleep")
    public void sleep(long second) {
        try {
            Thread.sleep(second * 1000L);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @Scheduled(cron = "0/5 * * * * ?")
    public void printll() {
        System.out.println("-----");
    }

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
