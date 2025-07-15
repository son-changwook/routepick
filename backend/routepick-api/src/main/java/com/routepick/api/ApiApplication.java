package com.routepick.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

/**
 * API 애플리케이션의 메인 클래스.
 * 일반 사용자를 위한 API 서비스를 제공합니다.
 */
@SpringBootApplication
@EnableScheduling
@EnableMethodSecurity
public class ApiApplication {
    public static void main(String[] args) {
        SpringApplication.run(ApiApplication.class, args);
    }
}