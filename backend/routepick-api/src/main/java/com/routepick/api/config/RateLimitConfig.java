package com.routepick.api.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Rate Limiting 설정 클래스
 */
@Data
@Component
@ConfigurationProperties(prefix = "rate-limit")
public class RateLimitConfig {
    
    private IpConfig ip = new IpConfig();
    private EmailConfig email = new EmailConfig();
    
    @Data
    public static class IpConfig {
        private int maxRequests = 100;
        private int windowSeconds = 60;
    }
    
    @Data
    public static class EmailConfig {
        private int maxRequests = 10;
        private int windowSeconds = 3600;
    }
}
