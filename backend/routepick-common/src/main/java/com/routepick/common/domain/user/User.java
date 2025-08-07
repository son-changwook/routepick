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
 *    - 닉네임은 UserDetails 테이블에서 관리됨
 * 
 * 3. 혼동 금지
 *    - getUsername() ≠ getUserName()
 *    - getUsername() = userId (String)
 *    - getUserName() = 사용자 실명
 * 
 * 4. 데이터베이스 필드
 *    - user_name: 사용자 실명
 *    - user_id: 사용자 식별자 (Long)
 *    - nick_name: 사용자 닉네임 (user_details 테이블)
 * 
 * 5. 사용 예시
 *    User user = userMapper.findById(userId);
 *    String userIdStr = user.getUsername();     // Spring Security 식별자
 *    String userName = user.getUserName();      // 사용자 실명
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

    /**
     * Spring Security에서 사용하는 사용자 식별자
     * JWT 토큰의 sub 필드와 일치하도록 userId를 반환
     * ⚠️ 주의: 이는 실제 사용자 이름(userName)이 아닌 식별자입니다.
     */
    @Override
    public String getUsername() {
        return String.valueOf(userId); // userId를 문자열로 반환
    }

    /**
     * 사용자 실명을 반환합니다.
     * Spring Security의 getUsername()과 구분하기 위해 별도 메서드로 제공합니다.
     * @return 사용자 실명
     */
    public String getUserName() {
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
