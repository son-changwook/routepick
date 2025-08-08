package com.routepick.api.service.user;

import com.routepick.api.dto.user.PersonalInfoUpdateRequest;
import com.routepick.api.dto.user.UserInfoResponse;
import com.routepick.api.mapper.UserMapper;
import com.routepick.common.domain.user.User;
import com.routepick.common.exception.customExceptions.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * 사용자 정보 서비스
 * users 테이블의 기본 회원 정보를 관리
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {
    
    private final UserMapper userMapper;
    
    /**
     * 사용자 정보 조회
     * @param userId 사용자 ID
     * @return 사용자 정보
     */
    @Transactional(readOnly = true)
    public UserInfoResponse getUserInfo(Long userId) {
        log.info("사용자 정보 조회 요청: userId={}", userId);
        
        Optional<User> userOpt = userMapper.findById(userId);
        if (userOpt.isEmpty()) {
            throw new ResourceNotFoundException("사용자를 찾을 수 없습니다.");
        }
        User user = userOpt.get();
        
        UserInfoResponse response = UserInfoResponse.builder()
                .userId(user.getUserId())
                .email(user.getEmail())
                .userName(user.getUserName())
                .nickName(user.getNickName())
                .phone(user.getPhone())
                .profileImageUrl(user.getProfileImageUrl())
                .birthDate(user.getBirthDate())
                .address(user.getAddress())
                .detailAddress(user.getDetailAddress())
                .emergencyContact(user.getEmergencyContact())
                .build();
        
        log.info("사용자 정보 조회 완료: userId={}", userId);
        return response;
    }
    
    /**
     * 사용자 정보 수정
     * @param userId 사용자 ID
     * @param request 수정 요청
     * @return 수정된 사용자 정보
     */
    @Transactional
    public UserInfoResponse updateUserInfo(Long userId, PersonalInfoUpdateRequest request) {
        log.info("사용자 정보 수정 요청: userId={}", userId);
        
        Optional<User> userOpt = userMapper.findById(userId);
        if (userOpt.isEmpty()) {
            throw new ResourceNotFoundException("사용자를 찾을 수 없습니다.");
        }
        User user = userOpt.get();
        
        // 사용자 정보 업데이트
        user.setUserName(request.getUserName());
        user.setPhone(request.getPhone());
        user.setBirthDate(request.getBirthDate() != null ? 
            java.time.LocalDate.parse(request.getBirthDate()) : null);
        user.setAddress(request.getAddress());
        user.setDetailAddress(request.getDetailAddress());
        user.setEmergencyContact(request.getEmergencyContact());
        
        userMapper.updateUser(user);
        
        UserInfoResponse response = UserInfoResponse.builder()
                .userId(user.getUserId())
                .email(user.getEmail())
                .userName(user.getUserName())
                .nickName(user.getNickName())
                .phone(user.getPhone())
                .profileImageUrl(user.getProfileImageUrl())
                .birthDate(user.getBirthDate())
                .address(user.getAddress())
                .detailAddress(user.getDetailAddress())
                .emergencyContact(user.getEmergencyContact())
                .build();
        
        log.info("사용자 정보 수정 완료: userId={}", userId);
        return response;
    }
}
