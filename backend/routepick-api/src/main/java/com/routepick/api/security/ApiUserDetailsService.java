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

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userMapper.findByUsername(username)
                .orElseThrow(() -> {
                    log.warn("User not found with username: {}", username);
                    return new UsernameNotFoundException("사용자를 찾을 수 없습니다: " + username);
                });

        // 일반 사용자만 접근 가능하도록 제한
        if (user.getUserType() != UserType.NORMAL) {
            log.warn("Admin user attempted to access user area: {}", username);
            throw new UsernameNotFoundException("일반 사용자만 접근 가능합니다: " + username);
        }

        if (!user.isEnabled()) {
            log.warn("Disabled user account attempted to login: {}", username);
            throw new UsernameNotFoundException("비활성화된 계정입니다: " + username);
        }

        return org.springframework.security.core.userdetails.User.builder()
                .username(user.getUsername())
                .password(user.getPassword())
                .disabled(!user.isEnabled())
                .accountExpired(!user.isAccountNonExpired())
                .credentialsExpired(!user.isCredentialsNonExpired())
                .accountLocked(!user.isAccountNonLocked())
                .authorities(user.getAuthorities())
                .build();
    }
}