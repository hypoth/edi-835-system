package com.healthcare.edi835;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Main application class for EDI 835 Remittance Processor
 * 
 * This application processes claims from Cosmos DB change feed and generates
 * EDI 835 remittance advice files based on configurable bucketing rules.
 */
@SpringBootApplication
@EnableScheduling
public class Edi835ProcessorApplication {

    public static void main(String[] args) {
        SpringApplication.run(Edi835ProcessorApplication.class, args);
    }
}