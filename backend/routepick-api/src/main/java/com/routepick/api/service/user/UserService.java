package com.routepick.api.service.user;

import org.springframework.stereotype.Service;

import com.routepick.api.mapper.UserMapper;
import com.routepick.common.domain.user.User;
import com.routepick.common.exception.customExceptions.UserNotFoundException;

import lombok.RequiredArgsConstructor;

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
     * 사용자 포인트 조회 (추후 구현)
     */
    public Integer getUserPoint(Long userId) {
        // TODO: 포인트 시스템 구현 후 실제 로직 추가
        return 0;
    }
    
    /**
     * 사용자 쿠폰 개수 조회 (추후 구현)
     */
    public Integer getUserCouponCount(Long userId) {
        // TODO: 쿠폰 시스템 구현 후 실제 로직 추가
        return 0;
    }
}
