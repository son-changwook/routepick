package com.routepick.api.config;

import org.springframework.boot.web.servlet.MultipartConfigFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartResolver;
import org.springframework.web.multipart.support.StandardServletMultipartResolver;

import jakarta.servlet.MultipartConfigElement;

/**
 * 파일 업로드 보안 설정
 * 파일 업로드 관련 보안 정책을 정의합니다.
 */
@Configuration
public class FileUploadSecurityConfig {

    /**
     * 파일 업로드 크기 제한 설정
     */
    @Bean
    public MultipartConfigElement multipartConfigElement() {
        MultipartConfigFactory factory = new MultipartConfigFactory();
        
        // 개별 파일 크기 제한 (5MB)
        factory.setMaxFileSize(DataSize.ofMegabytes(5));
        
        // 전체 요청 크기 제한 (10MB)
        factory.setMaxRequestSize(DataSize.ofMegabytes(10));
        
        // 임시 파일 위치 설정
        factory.setFileSizeThreshold(DataSize.ofKilobytes(512));
        
        return factory.createMultipartConfig();
    }

    /**
     * MultipartResolver 설정
     */
    @Bean
    public MultipartResolver multipartResolver() {
        StandardServletMultipartResolver resolver = new StandardServletMultipartResolver();
        
        // 파일 업로드 지원 활성화
        resolver.setResolveLazily(true);
        
        return resolver;
    }
}
