package com.routepick.api.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;

/**
 * 커스텀 UserDetails 구현체
 * Spring Security 컨벤션에 맞게 설계:
 * - getUsername(): 사용자 식별자 (userId)
 * - getEmail(): 이메일 주소
 * - getDisplayName(): 표시용 닉네임
 * - getProfileImageUrl(): 프로필 이미지 URL
 */
public class CustomUserDetails implements UserDetails {
    
    private final Long userId; // 사용자 식별자
    private final String email; // 이메일 주소
    private final String displayName; // 표시용 닉네임
    private final String profileImageUrl; // 프로필 이미지 URL
    private final String password;
    private final boolean enabled;
    private final boolean accountNonExpired;
    private final boolean credentialsNonExpired;
    private final boolean accountNonLocked;
    private final Collection<? extends GrantedAuthority> authorities;

    public CustomUserDetails(Long userId, String email, String displayName, String profileImageUrl, 
                           String password, boolean enabled, boolean accountNonExpired,
                           boolean credentialsNonExpired, boolean accountNonLocked,
                           Collection<? extends GrantedAuthority> authorities) {
        this.userId = userId;
        this.email = email;
        this.displayName = displayName;
        this.profileImageUrl = profileImageUrl;
        this.password = password;
        this.enabled = enabled;
        this.accountNonExpired = accountNonExpired;
        this.credentialsNonExpired = credentialsNonExpired;
        this.accountNonLocked = accountNonLocked;
        this.authorities = authorities;
    }

    /**
     * Spring Security에서 사용하는 사용자 식별자
     * JWT 토큰의 sub 필드와 일치하도록 userId를 반환
     */
    @Override
    public String getUsername() {
        return String.valueOf(userId); // userId를 문자열로 반환
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public boolean isAccountNonExpired() {
        return accountNonExpired;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return credentialsNonExpired;
    }

    @Override
    public boolean isAccountNonLocked() {
        return accountNonLocked;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    // 추가 정보 메서드들
    public Long getUserId() {
        return userId;
    }

    public String getEmail() {
        return email;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getProfileImageUrl() {
        return profileImageUrl;
    }
} 