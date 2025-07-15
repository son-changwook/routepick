package com.routepick.admin.security;

import com.routepick.common.domain.user.User;
import com.routepick.common.enums.UserType;
import com.routepick.admin.mapper.UserMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 사용자 정보를 로드하는 서비스.
 * Spring Security의 UserDetailsService를 구현하여 관리자 인증에 사용됩니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminUserDetailsService implements UserDetailsService {

    private final UserMapper userMapper;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        // 관리자 타입으로 사용자 조회 (ADMIN 또는 GYM_ADMIN)
        User user = userMapper.findByUsername(username)
                .orElseThrow(() -> {
                    log.warn("Admin not found with username: {}", username);
                    return new UsernameNotFoundException("관리자를 찾을 수 없습니다: " + username);
                });

        // 관리자 권한 확인
        if (user.getUserType() != UserType.ADMIN && user.getUserType() != UserType.GYM_ADMIN) {
            log.warn("Non-admin user attempted to access admin area: {}", username);
            throw new UsernameNotFoundException("관리자 권한이 없습니다: " + username);
        }

        if (!user.isEnabled()) {
            log.warn("Disabled admin account attempted to login: {}", username);
            throw new UsernameNotFoundException("비활성화된 관리자 계정입니다: " + username);
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