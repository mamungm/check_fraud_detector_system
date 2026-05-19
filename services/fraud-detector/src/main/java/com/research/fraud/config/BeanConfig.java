package com.research.fraud.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Configuration
public class BeanConfig {
    @Bean
    public Map<UUID, String> eventWSSessionMap() {
        return new ConcurrentHashMap<>();
    }
}
