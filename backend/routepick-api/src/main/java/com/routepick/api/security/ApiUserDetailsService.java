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
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApiUserDetailsService implements UserDetailsService {

    private final UserMapper userMapper;

    /**
     * userId로 사용자 정보를 로드합니다.
     * JWT 토큰의 sub 필드에서 userId가 전달됩니다.
     */
    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String userId) throws UsernameNotFoundException {
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

                       return new CustomUserDetails(
                       user.getEmail(), // 인증용 (JWT sub 필드)
                       user.getUsername(), // 마이페이지용 (닉네임)
                       user.getProfileImageUrl(), // 마이페이지용
                       user.getPassword(),
                       user.isEnabled(),
                       user.isAccountNonExpired(),
                       user.isCredentialsNonExpired(),
                       user.isAccountNonLocked(),
                       user.getAuthorities()
               );
    }
}