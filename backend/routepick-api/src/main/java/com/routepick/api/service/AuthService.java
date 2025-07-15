package com.routepick.api.service;

import com.routepick.api.dto.SignupRequest;
import com.routepick.api.mapper.UserMapper;
import com.routepick.common.domain.user.User;
import com.routepick.common.domain.agreement.UserAgreement;
import com.routepick.common.enums.UserStatus;
import com.routepick.common.enums.UserType;
import com.routepick.common.enums.AgreementType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final FileService fileService;

    /**
     * 회원가입 처리
     * @param request 회원가입 요청 정보
     * @param profileImage 프로필 이미지 파일 (선택사항)
     * @return 생성된 사용자 정보
     */
    @Transactional
    public User signup(SignupRequest request, MultipartFile profileImage) {
        
        // 1. 이메일 중복 확인
        if (userMapper.findByEmail(request.getEmail()) != null) {
            throw new IllegalArgumentException("이미 존재하는 이메일입니다.");
        }
        
        // 2. 비밀번호 해싱
        String hashedPassword = passwordEncoder.encode(request.getPassword());
        
        // 3. 프로필 이미지 업로드 (있는 경우)
        String profileImageUrl = null;
        if (profileImage != null && !profileImage.isEmpty()) {
            profileImageUrl = fileService.uploadProfileImage(profileImage);
        }
        
        // 4. User 객체 생성
        User user = User.builder()
                .email(request.getEmail())
                .passwordHash(hashedPassword)
                .userName(request.getUserName())
                .phone(request.getPhone())
                .profileImageUrl(profileImageUrl)
                .userType(UserType.NORMAL)
                .userStatus(UserStatus.ACTIVE)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        
        // 5. 사용자 저장
        userMapper.insertUser(user);
        
        // 6. 약관 동의 저장
        saveUserAgreements(user.getUserId(), request);
        
        log.info("새 사용자 등록 완료: {}", user.getEmail());
        
        return user;
    }

    /**
     * 사용자 약관 동의 저장
     * @param userId 사용자 ID
     * @param request 회원가입 요청 정보
     */
    private void saveUserAgreements(Long userId, SignupRequest request) {
        // TODO: UserAgreementMapper를 주입받아서 실제 DB 저장 로직 구현
        // 현재는 로그만 출력
        
        log.info("사용자 {} 약관 동의 저장:", userId);
        log.info("- 이용약관: {}", request.isRequiredAgreementValid() ? "동의" : "미동의");
        log.info("- 개인정보처리방침: {}", request.isRequiredAgreementValid() ? "동의" : "미동의");
        log.info("- 마케팅 수신: {}", request.isMarketingAgreed() ? "동의" : "미동의");
        log.info("- 위치정보 수집: {}", request.isLocationAgreed() ? "동의" : "미동의");
    }
} 