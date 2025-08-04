package com.routepick.common.domain.user;

import com.routepick.common.domain.common.BaseDomain;
import com.routepick.common.enums.UserType;
import com.routepick.common.enums.UserStatus;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;

/**
 * 사용자 도메인 클래스
 * Spring Security의 UserDetails를 구현하여 인증/인가에 사용됩니다.
 */
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
public class User extends BaseDomain implements UserDetails {
    
    private Long userId;
    private String email;
    private String passwordHash;
    private String userName;
    private String phone;
    private String profileImageUrl;
    private LocalDate birthDate;
    private String address;
    private String detailAddress;
    private String emergencyContact;
    private UserType userType;
    private UserStatus userStatus;
    private LocalDateTime lastLoginAt;

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        if (userType == null) {
            return Collections.emptyList();
        }
        
        // 사용자 타입에 따른 권한 부여
        // ADMIN: ROLE_ADMIN
        // GYM_ADMIN: ROLE_GYM_ADMIN
        // USER: ROLE_USER
        return Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + userType.name()));
        //getAuthorities() 메서드에서 반환하는 권한에 ROLE_ 접두사가 포함되어 있지만, @PreAuthorize에서는 이를 제외하고 사용합니다.
        //권한 확인 예시
        // 일반 사용자만 접근 가능
        //@PreAuthorize("hasRole('USER')")
        //@GetMapping("/profile")
        //public UserProfile getProfile() {
            // ...
        //}
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return userName;
    }

    @Override
    public boolean isAccountNonExpired() {
        return userStatus != UserStatus.DELETED;
    }

    @Override
    public boolean isAccountNonLocked() {
        return userStatus != UserStatus.SUSPENDED;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;    
    }

    @Override
    public boolean isEnabled() {
        return userStatus == UserStatus.ACTIVE;
    }   
    
    
    
    
    
    
    
    
    
 
}
