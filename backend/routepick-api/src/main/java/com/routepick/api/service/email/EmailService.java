package com.routepick.api.service.email;

import java.util.Random;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {
    
    private final JavaMailSender mailSender;
    
    @Value("${email.verification.from-email:noreply@routepick.com}")
    private String fromEmail;
    
    @Value("${email.verification.subject:[RoutePick] 이메일 인증 코드}")
    private String subject;
    
    @Value("${email.verification.template.verification-code}")
    private String emailTemplate;

    /**
     * 인증 코드 이메일 발송
     * @param toEmail 수신자 이메일
     * @param verificationCode 인증 코드
     */
     public void sendVerificationEmail(String toEmail, String verificationCode) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(toEmail);
            message.setSubject(subject);
            
            // 템플릿에서 인증 코드 치환
            String emailContent = emailTemplate.replace("{verificationCode}", verificationCode);
            message.setText(emailContent);
            
            mailSender.send(message);
            log.info("인증 코드 이메일 발송 완료: toEmail={}, code={}", toEmail, verificationCode);
            
        } catch (Exception e) {
            log.error("인증 코드 이메일 발송 실패: toEmail={}, error={}", toEmail, e.getMessage(), e);
            throw new RuntimeException("이메일 발송에 실패했습니다: " + e.getMessage(), e);
        }
     }
    
     /**
      * 인증 코드 생성 (6자리 숫자)
      * @return 생성된 인증 코드
      */
     public String generateVerificationCode() {
        return String.format("%06d", new Random().nextInt(1000000));
     }
}
