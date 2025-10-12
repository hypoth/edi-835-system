package com.healthcare.edi835;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class Edi835ProcessorApplication {
    public static void main(String[] args) {
        SpringApplication.run(Edi835ProcessorApplication.java, args);
    }
}
