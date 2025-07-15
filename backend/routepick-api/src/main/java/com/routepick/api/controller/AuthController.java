package com.routepick.api.controller;

import com.routepick.api.dto.SignupRequest;
import com.routepick.api.service.AuthService;
import com.routepick.common.domain.user.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.validation.Valid;

@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * 회원가입 API
     * @param request 회원가입 정보
     * @param profileImage 프로필 이미지 파일 (선택사항)
     * @return 회원가입 결과
     */
    @PostMapping("/signup")
    public ResponseEntity<?> signup(@RequestPart("userData") @Valid SignupRequest request,
                                   @RequestPart(value = "profileImage", required = false) MultipartFile profileImage) {
        
        log.info("회원가입 요청: {}", request.getEmail());
        
        try {
            User user = authService.signup(request, profileImage);
            
            log.info("회원가입 성공: {}", user.getEmail());
            return ResponseEntity.ok(user);
            
        } catch (Exception e) {
            log.error("회원가입 실패: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body("회원가입에 실패했습니다: " + e.getMessage());
        }
    }
} 