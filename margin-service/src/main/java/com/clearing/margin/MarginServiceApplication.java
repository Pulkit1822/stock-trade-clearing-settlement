package com.clearing.margin;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class MarginServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(MarginServiceApplication.class, args);
    }
}
