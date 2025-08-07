package com.routepick.api.service.user;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.routepick.api.dto.user.PersonalInfoResponse;
import com.routepick.api.dto.user.PersonalInfoUpdateRequest;
import com.routepick.api.mapper.UserMapper;
import com.routepick.api.util.InputSanitizer;
import com.routepick.api.util.SecureLogger;
import com.routepick.common.domain.user.User;
import com.routepick.common.enums.UserStatus;
import com.routepick.common.exception.customExceptions.UserNotFoundException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDate;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {
    
    private final UserMapper userMapper;
    
    /**
     * 이메일로 사용자 조회
     */
    public User getUserByEmail(String email) {
        return userMapper.findByEmail(email)
            .orElseThrow(() -> new UserNotFoundException("사용자를 찾을 수 없습니다."));
    }
    
    /**
     * 개인정보 조회
     * @param userId 사용자 ID
     * @return 개인정보 응답
     */
    public PersonalInfoResponse getPersonalInfo(Long userId) {
        log.info("개인정보 조회 요청: userId={}", userId);
        
        // 1. 사용자 조회
        User user = userMapper.findById(userId)
            .orElseThrow(() -> new UserNotFoundException("사용자를 찾을 수 없습니다."));
        
        // 2. 사용자 상태 확인 (보안 강화)
        if (user.getUserStatus() != UserStatus.ACTIVE) {
            SecureLogger.logSecurityEvent("비활성화된 사용자 개인정보 조회 시도: userId={}, status={}", 
                userId, user.getUserStatus());
            throw new UserNotFoundException("비활성화된 사용자입니다.");
        }
        
        // 3. 개인정보 응답 생성 (비밀번호 제외)
        PersonalInfoResponse response = PersonalInfoResponse.builder()
            .email(user.getEmail())
            .userName(user.getUserName()) // 사용자 실명
            .phone(user.getPhone())
            .profileImageUrl(user.getProfileImageUrl())
            .birthDate(user.getBirthDate())
            .address(user.getAddress())
            .detailAddress(user.getDetailAddress())
            .emergencyContact(user.getEmergencyContact())
            .build();
        
        log.info("개인정보 조회 완료: userId={}", userId);
        return response;
    }

    /**
     * 개인정보 수정
     * @param userId 사용자 ID
     * @param request 개인정보 수정 요청
     * @return 수정된 개인정보 응답
     */
    @Transactional
    public PersonalInfoResponse updatePersonalInfo(Long userId, PersonalInfoUpdateRequest request) {
        log.info("개인정보 수정 요청: userId={}", userId);
        
        // 1. 사용자 조회
        User user = userMapper.findById(userId)
            .orElseThrow(() -> new UserNotFoundException("사용자를 찾을 수 없습니다."));
        
        // 2. 사용자 상태 확인 (보안 강화)
        if (user.getUserStatus() != UserStatus.ACTIVE) {
            SecureLogger.logSecurityEvent("비활성화된 사용자 개인정보 수정 시도: userId={}, status={}", 
                userId, user.getUserStatus());
            throw new UserNotFoundException("비활성화된 사용자입니다.");
        }
        
        // 3. 생년월일 변환 (String -> LocalDate)
        LocalDate birthDate = null;
        if (request.getBirthDate() != null && !request.getBirthDate().trim().isEmpty()) {
            try {
                birthDate = LocalDate.parse(request.getBirthDate());
            } catch (Exception e) {
                log.error("생년월일 파싱 오류: {}", request.getBirthDate(), e);
                throw new IllegalArgumentException("올바른 생년월일 형식이 아닙니다. (YYYY-MM-DD)");
            }
        }
        
        // 4. 사용자 정보 업데이트
        User updatedUser = User.builder()
            .userId(userId)
            .email(user.getEmail()) // 이메일은 수정 불가
            .passwordHash(user.getPasswordHash()) // 비밀번호는 수정 불가
            .userName(request.getUserName())
            .phone(request.getPhone())
            .profileImageUrl(user.getProfileImageUrl()) // 프로필 이미지는 별도 API로 수정
            .birthDate(birthDate)
            .address(request.getAddress())
            .detailAddress(request.getDetailAddress())
            .emergencyContact(request.getEmergencyContact())
            .userType(user.getUserType())
            .userStatus(user.getUserStatus())
            .lastLoginAt(user.getLastLoginAt())
            .createdAt(user.getCreatedAt())
            .updatedAt(user.getUpdatedAt())
            .build();
        
        // 5. 데이터베이스 업데이트
        int updatedRows = userMapper.updateUser(updatedUser);
        if (updatedRows == 0) {
            SecureLogger.logSecurityEvent("개인정보 수정 실패: userId={}", userId);
            throw new UserNotFoundException("사용자 정보 수정에 실패했습니다.");
        }
        
        // 6. 수정된 개인정보 조회하여 반환
        PersonalInfoResponse response = PersonalInfoResponse.builder()
            .email(updatedUser.getEmail())
            .userName(updatedUser.getUserName()) // 사용자 실명
            .phone(updatedUser.getPhone())
            .profileImageUrl(updatedUser.getProfileImageUrl())
            .birthDate(updatedUser.getBirthDate())
            .address(updatedUser.getAddress())
            .detailAddress(updatedUser.getDetailAddress())
            .emergencyContact(updatedUser.getEmergencyContact())
            .build();
        
        log.info("개인정보 수정 완료: userId={}", userId);
        return response;
    }
}
