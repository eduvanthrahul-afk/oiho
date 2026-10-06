package com.decisiontwin;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication @EnableAsync
public class DecisionTwinApplication {
    public static void main(String[] args) { SpringApplication.run(DecisionTwinApplication.class, args); }
}
