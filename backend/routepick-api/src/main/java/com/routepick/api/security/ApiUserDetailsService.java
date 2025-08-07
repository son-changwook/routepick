package com.routepick.api.security;

import com.routepick.common.domain.user.User;
import com.routepick.common.enums.UserType;
import com.routepick.api.mapper.UserMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 사용자 정보를 로드하는 서비스.
 * Spring Security의 UserDetailsService를 구현하여 사용자 인증에 사용됩니다.
 * JWT 토큰의 sub 필드에서 userId를 받아 사용자 정보를 로드합니다.
 * 
 * ⚠️ userName 관련 주의사항:
 * 1. user.getUsername() = userId (String) - Spring Security 식별자
 * 2. user.getUserName() = 실제 사용자 이름 (닉네임)
 * 3. 혼동 금지: getUsername() ≠ getUserName()
 * 4. CustomUserDetails 생성 시 올바른 메서드 사용 필수
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApiUserDetailsService implements UserDetailsService {

    private final UserMapper userMapper;

    /**
     * userId로 사용자 정보를 로드합니다.
     * JWT 토큰의 sub 필드에서 userId가 전달됩니다.
     * 
     * @param userId 사용자 ID (문자열)
     * @return UserDetails 객체
     * @throws UsernameNotFoundException 사용자를 찾을 수 없는 경우
     */
    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String userId) throws UsernameNotFoundException {
        try {
            // JWT 토큰에서 userId가 전달되므로 findById 사용
            User user = userMapper.findById(Long.parseLong(userId))
                    .orElseThrow(() -> {
                        log.warn("User not found with userId: {}", userId);
                        return new UsernameNotFoundException("사용자를 찾을 수 없습니다: " + userId);
                    });

            // 일반 사용자만 접근 가능하도록 제한
            if (user.getUserType() != UserType.NORMAL) {
                log.warn("Admin user attempted to access user area: {}", userId);
                throw new UsernameNotFoundException("일반 사용자만 접근 가능합니다: " + userId);
            }

            if (!user.isEnabled()) {
                log.warn("Disabled user account attempted to login: {}", userId);
                throw new UsernameNotFoundException("비활성화된 계정입니다: " + userId);
            }

            // ⚠️ 주의: user.getUserName() 사용 (실제 사용자 이름)
            return new CustomUserDetails(
                    user.getUserId(), // 사용자 식별자
                    user.getEmail(), // 이메일 주소
                    user.getUserName(), // 실제 사용자 이름 (닉네임)
                    user.getProfileImageUrl(), // 프로필 이미지 URL
                    user.getPassword(),
                    user.isEnabled(),
                    user.isAccountNonExpired(),
                    user.isCredentialsNonExpired(),
                    user.isAccountNonLocked(),
                    user.getAuthorities()
            );
        } catch (NumberFormatException e) {
            log.error("Invalid userId format: {}", userId);
            throw new UsernameNotFoundException("유효하지 않은 사용자 ID 형식입니다: " + userId);
        }
    }
}