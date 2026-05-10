package com.sentinel.iot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SentinelIotApplication {
    public static void main(String[] args) {
        SpringApplication.run(SentinelIotApplication.class, args);
    }
}
