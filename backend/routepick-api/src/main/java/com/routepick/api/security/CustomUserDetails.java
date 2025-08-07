package com.routepick.api.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;

/**
 * 커스텀 UserDetails 구현체
 * 
 * ⚠️ 중요: userName과 nickName 관련 주의사항
 * 
 * 1. Spring Security 컨벤션
 *    - getUsername(): 반드시 String 타입, 사용자 식별자 (userId)
 *    - Spring Security의 UserDetails 인터페이스 규칙
 *    - JWT 토큰의 sub 필드와 일치해야 함
 * 
 * 2. 실제 사용자 정보
 *    - getUserName(): 사용자 실명 (홍길동)
 *    - getNickName(): 사용자 닉네임 (climber123) - UI 표시 우선 사용
 * 
 * 3. 혼동 금지
 *    - getUsername() ≠ getUserName() ≠ getNickName()
 *    - getUsername() = userId (String) - Spring Security 식별자
 *    - getUserName() = 사용자 실명
 *    - getNickName() = 사용자 닉네임 (UI 표시 우선)
 * 
 * 4. JWT 토큰 구조
 *    {
 *      "sub": "123",           // userId (String)
 *      "userName": "홍길동",      // 사용자 실명
 *      "nickName": "climber123", // 사용자 닉네임
 *      "email": "user@example.com"
 *    }
 * 
 * 5. 사용 예시
 *    @AuthenticationPrincipal CustomUserDetails userDetails
 *    Long userId = userDetails.getUserId();           // 사용자 식별자
 *    String userName = userDetails.getUserName();     // 사용자 실명
 *    String nickName = userDetails.getNickName();     // 사용자 닉네임 (UI 표시 우선)
 */
public class CustomUserDetails implements UserDetails {
    
    private final Long userId; // 사용자 식별자
    private final String email; // 이메일 주소
    private final String userName; // 사용자 실명
    private final String nickName; // 사용자 닉네임
    private final String profileImageUrl; // 프로필 이미지 URL
    private final String password;
    private final boolean enabled;
    private final boolean accountNonExpired;
    private final boolean credentialsNonExpired;
    private final boolean accountNonLocked;
    private final Collection<? extends GrantedAuthority> authorities;

    public CustomUserDetails(Long userId, String email, String userName, String nickName, String profileImageUrl, 
                           String password, boolean enabled, boolean accountNonExpired,
                           boolean credentialsNonExpired, boolean accountNonLocked,
                           Collection<? extends GrantedAuthority> authorities) {
        this.userId = userId;
        this.email = email;
        this.userName = userName;
        this.nickName = nickName;
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
     * ⚠️ 주의: 이는 실제 사용자 이름(userName)이 아닌 식별자입니다.
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

    /**
     * 사용자 실명을 반환합니다.
     * @return 사용자 실명
     */
    public String getUserName() {
        return userName;
    }

    /**
     * 사용자 닉네임을 반환합니다.
     * @return 사용자 닉네임
     */
    public String getNickName() {
        return nickName;
    }

    public String getProfileImageUrl() {
        return profileImageUrl;
    }
} 