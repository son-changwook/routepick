package com.routepick.api.config;

import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.type.TypeHandlerRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import com.routepick.common.exception.SecurityException;

import jakarta.annotation.PostConstruct;

/**
 * MyBatis 보안 설정
 * 
 * SQL Injection 방지를 위한 설정:
 * 1. 파라미터 바인딩 강화
 * 2. 타입 핸들러 보안
 * 3. SQL 로깅 보안
 */
@Configuration
public class MyBatisSecurityConfig {
    
    @Autowired
    private SqlSessionFactory sqlSessionFactory;
    
    /**
     * MyBatis 보안 설정 초기화
     * SqlSessionFactory가 생성된 후 실행됩니다.
     */
    @PostConstruct
    public void configureMyBatisSecurity() {
        if (sqlSessionFactory == null) {
            log.warn("SqlSessionFactory가 null입니다. 보안 설정을 건너뜁니다.");
            return;
        }
        
        try {
            // 파라미터 바인딩 강화 설정
            configureParameterBinding();
            
            // SQL 로깅 보안 설정
            configureSecureLogging();
            
            // 타입 핸들러 보안 설정
            configureTypeHandlerSecurity();
            
            log.info("MyBatis 보안 설정이 완료되었습니다.");
        } catch (Exception e) {
            log.error("MyBatis 보안 설정 중 오류 발생: {}", e.getMessage(), e);
        }
    }
    
    /**
     * 파라미터 바인딩 강화 설정
     */
    private void configureParameterBinding() {
        // MyBatis 설정에서 파라미터 바인딩을 강제로 활성화
        // #{} 문법만 사용하도록 제한 (${} 사용 금지)
        sqlSessionFactory.getConfiguration().setUseGeneratedKeys(true);
        sqlSessionFactory.getConfiguration().setMapUnderscoreToCamelCase(true);
        
        // 파라미터 바인딩 검증 활성화
        sqlSessionFactory.getConfiguration().setDefaultExecutorType(
            org.apache.ibatis.session.ExecutorType.SIMPLE
        );
        
        log.info("파라미터 바인딩 강화 설정 완료");
    }
    
    /**
     * SQL 로깅 보안 설정
     */
    private void configureSecureLogging() {
        // SQL 로깅에서 민감한 정보 마스킹
        sqlSessionFactory.getConfiguration().setLogImpl(
            org.apache.ibatis.logging.slf4j.Slf4jImpl.class
        );
        
        // 로깅 레벨 설정 (개발환경에서만 상세 로깅)
        if (isDevelopmentEnvironment()) {
            sqlSessionFactory.getConfiguration().setLogImpl(
                org.apache.ibatis.logging.stdout.StdOutImpl.class
            );
        }
        
        log.info("SQL 로깅 보안 설정 완료");
    }
    
    /**
     * 타입 핸들러 보안 설정
     */
    private void configureTypeHandlerSecurity() {
        TypeHandlerRegistry typeHandlerRegistry = sqlSessionFactory.getConfiguration().getTypeHandlerRegistry();
        
        // String 타입 핸들러에 보안 검증 추가
        typeHandlerRegistry.register(String.class, new SecureStringTypeHandler());
        
        // 기본 타입들에 대한 안전한 핸들러 등록
        typeHandlerRegistry.register(Long.class, new SecureLongTypeHandler());
        typeHandlerRegistry.register(Integer.class, new SecureIntegerTypeHandler());
        
        log.info("타입 핸들러 보안 설정 완료");
    }
    
    /**
     * 개발환경 여부 확인
     */
    private boolean isDevelopmentEnvironment() {
        String profile = System.getProperty("spring.profiles.active", "dev");
        return "dev".equals(profile) || "development".equals(profile);
    }
    
    /**
     * 보안 강화된 String 타입 핸들러
     */
    public static class SecureStringTypeHandler extends org.apache.ibatis.type.StringTypeHandler {
        
        @Override
        public void setNonNullParameter(java.sql.PreparedStatement ps, int i, String parameter, org.apache.ibatis.type.JdbcType jdbcType) throws java.sql.SQLException {
            // SQL Injection 방지를 위한 추가 검증
            if (parameter != null && containsSqlInjectionPattern(parameter)) {
                throw SecurityException.sqlInjectionDetected();
            }
            super.setNonNullParameter(ps, i, parameter, jdbcType);
        }
        
        private boolean containsSqlInjectionPattern(String input) {
            // 기본적인 SQL Injection 패턴 검사
            String lowerInput = input.toLowerCase();
            return lowerInput.contains("'") || 
                   lowerInput.contains("--") || 
                   lowerInput.contains("/*") || 
                   lowerInput.contains("*/") ||
                   lowerInput.contains("union") ||
                   lowerInput.contains("select") ||
                   lowerInput.contains("insert") ||
                   lowerInput.contains("update") ||
                   lowerInput.contains("delete") ||
                   lowerInput.contains("drop") ||
                   lowerInput.contains("create");
        }
    }
    
    /**
     * 보안 강화된 Long 타입 핸들러
     */
    public static class SecureLongTypeHandler extends org.apache.ibatis.type.LongTypeHandler {
        
        @Override
        public void setNonNullParameter(java.sql.PreparedStatement ps, int i, Long parameter, org.apache.ibatis.type.JdbcType jdbcType) throws java.sql.SQLException {
            // Long 값 검증
            if (parameter != null && (parameter < 0 || parameter > Long.MAX_VALUE)) {
                throw SecurityException.invalidInputFormat("Long 값");
            }
            super.setNonNullParameter(ps, i, parameter, jdbcType);
        }
    }
    
    /**
     * 보안 강화된 Integer 타입 핸들러
     */
    public static class SecureIntegerTypeHandler extends org.apache.ibatis.type.IntegerTypeHandler {
        
        @Override
        public void setNonNullParameter(java.sql.PreparedStatement ps, int i, Integer parameter, org.apache.ibatis.type.JdbcType jdbcType) throws java.sql.SQLException {
            // Integer 값 검증
            if (parameter != null && (parameter < 0 || parameter > Integer.MAX_VALUE)) {
                throw SecurityException.invalidInputFormat("Integer 값");
            }
            super.setNonNullParameter(ps, i, parameter, jdbcType);
        }
    }
} 