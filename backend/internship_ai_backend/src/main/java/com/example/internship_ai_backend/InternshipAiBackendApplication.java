package com.example.internship_ai_backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class InternshipAiBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(InternshipAiBackendApplication.class, args);
    }

}
