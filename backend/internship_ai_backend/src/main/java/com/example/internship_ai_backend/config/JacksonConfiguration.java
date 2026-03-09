package com.example.internship_ai_backend.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JacksonConfiguration {

    @Bean
    public ObjectMapper objectMapper() {
        // Register available Jackson modules (including JavaTimeModule for LocalDateTime).
        return new ObjectMapper().findAndRegisterModules();
    }
}
