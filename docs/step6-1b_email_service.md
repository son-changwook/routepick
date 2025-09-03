# Step 6-1b: EmailService 구현

> 비동기 이메일 발송 및 Redis 기반 인증 코드 관리 완전 구현  
> 생성일: 2025-08-20  
> 기반: step5-1a,b,c_repositories.md, Thymeleaf 템플릿 및 Redis TTL 전략

---

## 🎯 설계 목표

- **비동기 처리**: @Async를 통한 이메일 발송 최적화
- **Redis 캐싱**: 인증 코드, 재발송 쿨타임 관리
- **템플릿 엔진**: Thymeleaf 기반 HTML 이메일 생성
- **보안 강화**: 6자리 랜덤 인증 코드, TTL 관리
- **다양한 알림**: 인증, 환영, 비밀번호 재설정, 계정 정지 등

---

## 📧 EmailService - 이메일 서비스

### EmailService.java
```java
package com.routepick.service.email;

import com.routepick.domain.user.entity.User;
import com.routepick.exception.system.SystemException;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

/**
 * 이메일 서비스
 * - 비동기 이메일 발송
 * - Redis 기반 인증 코드 관리
 * - 템플릿 기반 이메일 생성
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {
    
    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;
    private final RedisTemplate<String, Object> redisTemplate;
    
    @Value("${app.email.from}")
    private String fromEmail;
    
    @Value("${app.email.from-name}")
    private String fromName;
    
    @Value("${app.email.verification.ttl-minutes:5}")
    private int verificationCodeTtlMinutes;
    
    @Value("${app.email.verification.cooldown-seconds:30}")
    private int verificationCooldownSeconds;
    
    @Value("${app.frontend.url}")
    private String frontendUrl;
    
    private static final String VERIFICATION_CODE_PREFIX = "email:verification:";
    private static final String COOLDOWN_PREFIX = "email:cooldown:";
    private static final String CHARACTERS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final SecureRandom random = new SecureRandom();
    
    // ===== 이메일 인증 =====
    
    /**
     * 이메일 인증 코드 발송
     */
    @Async
    public void sendVerificationEmail(User user) {
        log.info("이메일 인증 발송 시작: userId={}, email={}", user.getUserId(), user.getEmail());
        
        // 재발송 쿨타임 확인
        if (!checkCooldown(user.getEmail())) {
            log.warn("이메일 재발송 쿨타임 중: email={}", user.getEmail());
            return;
        }
        
        // 인증 코드 생성
        String verificationCode = generateVerificationCode();
        
        // Redis에 저장 (TTL 5분)
        String key = VERIFICATION_CODE_PREFIX + user.getEmail();
        redisTemplate.opsForValue().set(key, verificationCode, 
            verificationCodeTtlMinutes, TimeUnit.MINUTES);
        
        // 쿨타임 설정 (30초)
        setCooldown(user.getEmail());
        
        // 이메일 발송
        Context context = new Context();
        context.setVariable("userName", user.getUserName());
        context.setVariable("verificationCode", verificationCode);
        context.setVariable("expirationMinutes", verificationCodeTtlMinutes);
        
        String subject = "[RoutePickr] 이메일 인증 코드";
        String html = templateEngine.process("email/verification", context);
        
        sendHtmlEmail(user.getEmail(), subject, html);
        
        log.info("이메일 인증 발송 완료: email={}", user.getEmail());
    }
    
    /**
     * 회원가입 확인 메일
     */
    @Async
    public void sendWelcomeEmail(User user) {
        log.info("회원가입 확인 메일 발송: userId={}, email={}", user.getUserId(), user.getEmail());
        
        Context context = new Context();
        context.setVariable("userName", user.getUserName());
        context.setVariable("nickName", user.getNickName());
        context.setVariable("loginUrl", frontendUrl + "/login");
        context.setVariable("profileUrl", frontendUrl + "/profile");
        
        String subject = "[RoutePickr] 회원가입을 환영합니다!";
        String html = templateEngine.process("email/welcome", context);
        
        sendHtmlEmail(user.getEmail(), subject, html);
    }
    
    /**
     * 비밀번호 재설정 메일
     */
    @Async
    public void sendPasswordResetEmail(User user, String resetToken) {
        log.info("비밀번호 재설정 메일 발송: userId={}, email={}", user.getUserId(), user.getEmail());
        
        String resetUrl = frontendUrl + "/reset-password?token=" + resetToken;
        
        Context context = new Context();
        context.setVariable("userName", user.getUserName());
        context.setVariable("resetUrl", resetUrl);
        context.setVariable("expirationHours", 1);
        
        String subject = "[RoutePickr] 비밀번호 재설정";
        String html = templateEngine.process("email/password-reset", context);
        
        sendHtmlEmail(user.getEmail(), subject, html);
    }
    
    // ===== 인증 코드 관리 =====
    
    /**
     * 인증 코드 검증
     */
    public boolean verifyCode(String email, String code) {
        String key = VERIFICATION_CODE_PREFIX + email;
        String storedCode = (String) redisTemplate.opsForValue().get(key);
        
        if (storedCode == null) {
            log.warn("인증 코드 만료 또는 미존재: email={}", email);
            return false;
        }
        
        boolean isValid = storedCode.equals(code);
        
        if (isValid) {
            // 검증 성공 시 코드 삭제
            redisTemplate.delete(key);
            log.info("인증 코드 검증 성공: email={}", email);
        } else {
            log.warn("인증 코드 불일치: email={}, inputCode={}", email, code);
        }
        
        return isValid;
    }
    
    /**
     * 인증 코드 재발송 가능 여부 확인
     */
    public boolean canResendVerification(String email) {
        return checkCooldown(email);
    }
    
    /**
     * 인증 코드 남은 시간 조회 (초)
     */
    public Long getVerificationCodeTtl(String email) {
        String key = VERIFICATION_CODE_PREFIX + email;
        return redisTemplate.getExpire(key, TimeUnit.SECONDS);
    }
    
    // ===== Helper 메서드 =====
    
    /**
     * HTML 이메일 발송
     */
    private void sendHtmlEmail(String to, String subject, String html) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            
            helper.setFrom(fromEmail, fromName);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(html, true);
            
            mailSender.send(message);
            
            log.info("이메일 발송 성공: to={}, subject={}", to, subject);
            
        } catch (Exception e) {
            log.error("이메일 발송 실패: to={}, subject={}", to, subject, e);
            throw new SystemException("이메일 발송 실패", e);
        }
    }
    
    /**
     * 인증 코드 생성 (6자리)
     */
    private String generateVerificationCode() {
        StringBuilder code = new StringBuilder(6);
        for (int i = 0; i < 6; i++) {
            code.append(CHARACTERS.charAt(random.nextInt(CHARACTERS.length())));
        }
        return code.toString();
    }
    
    /**
     * 재발송 쿨타임 확인
     */
    private boolean checkCooldown(String email) {
        String key = COOLDOWN_PREFIX + email;
        return !Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }
    
    /**
     * 재발송 쿨타임 설정
     */
    private void setCooldown(String email) {
        String key = COOLDOWN_PREFIX + email;
        redisTemplate.opsForValue().set(key, true, 
            verificationCooldownSeconds, TimeUnit.SECONDS);
    }
    
    // ===== 템플릿 관리 =====
    
    /**
     * 알림 이메일 발송 (범용)
     */
    @Async
    public void sendNotificationEmail(String to, String userName, String title, String content) {
        Context context = new Context();
        context.setVariable("userName", userName);
        context.setVariable("title", title);
        context.setVariable("content", content);
        context.setVariable("timestamp", LocalDateTime.now());
        
        String subject = "[RoutePickr] " + title;
        String html = templateEngine.process("email/notification", context);
        
        sendHtmlEmail(to, subject, html);
    }
    
    /**
     * 계정 정지 알림
     */
    @Async
    public void sendAccountSuspendedEmail(User user, String reason) {
        Context context = new Context();
        context.setVariable("userName", user.getUserName());
        context.setVariable("reason", reason);
        context.setVariable("supportEmail", "support@routepickr.com");
        
        String subject = "[RoutePickr] 계정 정지 알림";
        String html = templateEngine.process("email/account-suspended", context);
        
        sendHtmlEmail(user.getEmail(), subject, html);
    }
    
    /**
     * 로그인 알림 (새로운 디바이스)
     */
    @Async
    public void sendNewDeviceLoginEmail(User user, String deviceInfo, String ipAddress) {
        Context context = new Context();
        context.setVariable("userName", user.getUserName());
        context.setVariable("deviceInfo", deviceInfo);
        context.setVariable("ipAddress", ipAddress);
        context.setVariable("loginTime", LocalDateTime.now());
        context.setVariable("securityUrl", frontendUrl + "/security");
        
        String subject = "[RoutePickr] 새로운 디바이스에서 로그인";
        String html = templateEngine.process("email/new-device-login", context);
        
        sendHtmlEmail(user.getEmail(), subject, html);
    }
}
```

---

## 📧 이메일 템플릿 예시

### verification.html (이메일 인증)
```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>이메일 인증</title>
    <style>
        body { font-family: Arial, sans-serif; line-height: 1.6; margin: 0; padding: 20px; }
        .container { max-width: 600px; margin: 0 auto; background: #f9f9f9; padding: 20px; border-radius: 10px; }
        .header { text-align: center; margin-bottom: 30px; }
        .code { font-size: 32px; font-weight: bold; color: #007bff; text-align: center; 
                letter-spacing: 5px; padding: 20px; background: #fff; border-radius: 5px; }
        .footer { margin-top: 30px; text-align: center; color: #666; }
    </style>
</head>
<body>
    <div class="container">
        <div class="header">
            <h1>🧗‍♀️ RoutePickr</h1>
            <h2>이메일 인증 코드</h2>
        </div>
        
        <p>안녕하세요, <strong th:text="${userName}">사용자</strong>님!</p>
        
        <p>RoutePickr 가입을 위한 이메일 인증 코드입니다.</p>
        
        <div class="code" th:text="${verificationCode}">123456</div>
        
        <p>위 인증 코드를 앱에 입력해 주세요.</p>
        
        <p><strong>유효시간:</strong> <span th:text="${expirationMinutes}">5</span>분</p>
        
        <div class="footer">
            <p>본인이 요청하지 않은 경우, 이 메일을 무시해 주세요.</p>
            <p>© 2024 RoutePickr. All rights reserved.</p>
        </div>
    </div>
</body>
</html>
```

### welcome.html (환영 메일)
```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>회원가입 환영</title>
    <style>
        body { font-family: Arial, sans-serif; line-height: 1.6; margin: 0; padding: 20px; }
        .container { max-width: 600px; margin: 0 auto; background: #f9f9f9; padding: 20px; border-radius: 10px; }
        .header { text-align: center; margin-bottom: 30px; }
        .welcome { background: #007bff; color: white; padding: 20px; border-radius: 5px; text-align: center; }
        .button { display: inline-block; background: #28a745; color: white; padding: 12px 20px; 
                  text-decoration: none; border-radius: 5px; margin: 10px 5px; }
        .features { margin: 20px 0; }
        .feature { margin: 10px 0; padding-left: 20px; }
    </style>
</head>
<body>
    <div class="container">
        <div class="header">
            <h1>🧗‍♀️ RoutePickr</h1>
        </div>
        
        <div class="welcome">
            <h2>환영합니다!</h2>
            <p><strong th:text="${nickName}">닉네임</strong>님, RoutePickr 가입을 축하드립니다!</p>
        </div>
        
        <div class="features">
            <h3>🎯 이제 이런 기능을 사용할 수 있어요:</h3>
            <div class="feature">🏷️ AI 기반 루트 추천</div>
            <div class="feature">🗺️ 주변 암장 찾기</div>
            <div class="feature">📱 클라이밍 기록 관리</div>
            <div class="feature">👥 클라이머들과 소통</div>
        </div>
        
        <div style="text-align: center; margin: 30px 0;">
            <a th:href="${loginUrl}" class="button">로그인하기</a>
            <a th:href="${profileUrl}" class="button">프로필 설정</a>
        </div>
        
        <div style="margin-top: 30px; text-align: center; color: #666;">
            <p>더 나은 클라이밍 경험을 위해 RoutePickr와 함께하세요!</p>
            <p>© 2024 RoutePickr. All rights reserved.</p>
        </div>
    </div>
</body>
</html>
```

### password-reset.html (비밀번호 재설정)
```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>비밀번호 재설정</title>
    <style>
        body { font-family: Arial, sans-serif; line-height: 1.6; margin: 0; padding: 20px; }
        .container { max-width: 600px; margin: 0 auto; background: #f9f9f9; padding: 20px; border-radius: 10px; }
        .header { text-align: center; margin-bottom: 30px; }
        .reset-button { display: inline-block; background: #dc3545; color: white; padding: 15px 30px; 
                        text-decoration: none; border-radius: 5px; margin: 20px 0; }
        .warning { background: #fff3cd; color: #856404; padding: 15px; border-radius: 5px; margin: 20px 0; }
    </style>
</head>
<body>
    <div class="container">
        <div class="header">
            <h1>🧗‍♀️ RoutePickr</h1>
            <h2>비밀번호 재설정</h2>
        </div>
        
        <p>안녕하세요, <strong th:text="${userName}">사용자</strong>님!</p>
        
        <p>비밀번호 재설정 요청을 받았습니다.</p>
        
        <div style="text-align: center;">
            <a th:href="${resetUrl}" class="reset-button">비밀번호 재설정하기</a>
        </div>
        
        <div class="warning">
            <strong>⚠️ 주의사항:</strong>
            <ul>
                <li>이 링크는 <span th:text="${expirationHours}">1</span>시간 후 만료됩니다.</li>
                <li>본인이 요청하지 않은 경우, 이 메일을 무시해 주세요.</li>
                <li>비밀번호는 영문, 숫자, 특수문자를 포함해 8자 이상으로 설정해 주세요.</li>
            </ul>
        </div>
        
        <div style="margin-top: 30px; text-align: center; color: #666;">
            <p>계정 보안에 문제가 있다면 즉시 고객지원에 문의해 주세요.</p>
            <p>© 2024 RoutePickr. All rights reserved.</p>
        </div>
    </div>
</body>
</html>
```

---

## ✅ 설계 완료 체크리스트

### 📧 이메일 발송 기능
- [x] 이메일 인증 코드 발송 (@Async 비동기 처리)
- [x] 회원가입 확인 메일 (환영 메시지)
- [x] 비밀번호 재설정 메일 (토큰 링크)
- [x] 계정 정지 알림 메일
- [x] 새로운 디바이스 로그인 알림

### 🔒 보안 및 검증
- [x] Redis 기반 인증 코드 관리 (TTL 5분)
- [x] 인증 재발송 제한 (30초 쿨타임)
- [x] 6자리 랜덤 인증 코드 생성 (0-9, A-Z)
- [x] 인증 코드 검증 후 자동 삭제
- [x] HTML 이메일 템플릿 XSS 방지

### 📋 템플릿 관리
- [x] Thymeleaf 기반 HTML 이메일 생성
- [x] 반응형 이메일 디자인 (모바일 최적화)
- [x] 브랜드 일관성 유지 (RoutePickr 로고/색상)
- [x] 다국어 지원 준비 (변수 기반 텍스트)
- [x] 범용 알림 이메일 템플릿

---

**다음 파일**: Step 6-1c UserService 구현  
**핵심 목표**: 사용자 관리, 프로필, 팔로우 시스템 구현

*완료일: 2025-08-20*  
*핵심 성과: 비동기 이메일 시스템 및 Redis 인증 코드 관리 완전 구현*