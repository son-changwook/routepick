# Step 8-2c: 메서드 보안 설정 구현

> @PreAuthorize, @PostAuthorize 기반 권한 제어 및 접근 제한 설정  
> 생성일: 2025-08-26  
> 기반 파일: step4-1b_user_core_entities.md (권한 체계)

---

## 🎯 구현 목표

- **메서드 레벨 보안**: @PreAuthorize, @PostAuthorize 활용
- **역할 기반 접근 제어**: USER, ADMIN, GYM_OWNER 권한 관리
- **리소스 소유권 검증**: 사용자별 리소스 접근 제어
- **동적 권한 평가**: SpEL 표현식 기반 복잡한 권한 로직
- **보안 감사**: 권한 검사 및 실패 이벤트 로깅

---

## 🔐 1. MethodSecurityConfig 설정

### MethodSecurityConfig.java
```java
package com.routepick.config;

import com.routepick.security.CustomMethodSecurityExpressionHandler;
import com.routepick.security.CustomPermissionEvaluator;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler;
import org.springframework.security.access.expression.method.MethodSecurityExpressionHandler;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.method.configuration.GlobalMethodSecurityConfiguration;

/**
 * 메서드 보안 설정
 * - @PreAuthorize, @PostAuthorize 활성화
 * - 커스텀 권한 평가기 설정
 * - SpEL 표현식 확장
 */
@Configuration
@EnableMethodSecurity(
    prePostEnabled = true,      // @PreAuthorize, @PostAuthorize 활성화
    securedEnabled = true,      // @Secured 활성화
    jsr250Enabled = true        // @RolesAllowed 활성화
)
@RequiredArgsConstructor
public class MethodSecurityConfig {
    
    private final CustomPermissionEvaluator permissionEvaluator;
    
    /**
     * 메서드 보안 표현식 핸들러
     */
    @Bean
    public MethodSecurityExpressionHandler methodSecurityExpressionHandler() {
        CustomMethodSecurityExpressionHandler expressionHandler = 
            new CustomMethodSecurityExpressionHandler();
        expressionHandler.setPermissionEvaluator(permissionEvaluator);
        return expressionHandler;
    }
}
```

---

## 🛡️ 2. CustomPermissionEvaluator 구현

### CustomPermissionEvaluator.java
```java
package com.routepick.security;

import com.routepick.common.enums.UserType;
import com.routepick.domain.user.entity.User;
import com.routepick.domain.user.repository.UserRepository;
import com.routepick.domain.gym.entity.Gym;
import com.routepick.domain.gym.entity.GymMember;
import com.routepick.domain.gym.repository.GymRepository;
import com.routepick.domain.gym.repository.GymMemberRepository;
import com.routepick.domain.route.entity.Route;
import com.routepick.domain.route.repository.RouteRepository;
import com.routepick.domain.community.entity.Post;
import com.routepick.domain.community.repository.PostRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.PermissionEvaluator;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.io.Serializable;
import java.util.Optional;

/**
 * 커스텀 권한 평가기
 * - 리소스별 접근 권한 검증
 * - 소유권 기반 접근 제어
 * - 계층적 권한 관리
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CustomPermissionEvaluator implements PermissionEvaluator {
    
    private final UserRepository userRepository;
    private final GymRepository gymRepository;
    private final GymMemberRepository gymMemberRepository;
    private final RouteRepository routeRepository;
    private final PostRepository postRepository;
    
    @Override
    public boolean hasPermission(Authentication authentication, Object targetDomainObject, Object permission) {
        if (authentication == null || targetDomainObject == null) {
            return false;
        }
        
        String username = authentication.getName();
        String permissionString = permission.toString().toLowerCase();
        
        // 사용자 조회
        Optional<User> userOpt = userRepository.findByEmail(username);
        if (userOpt.isEmpty()) {
            return false;
        }
        
        User user = userOpt.get();
        
        // 관리자는 모든 권한 보유
        if (UserType.ADMIN.equals(user.getUserType())) {
            return true;
        }
        
        // 객체별 권한 검사
        return switch (targetDomainObject.getClass().getSimpleName()) {
            case "User" -> evaluateUserPermission(user, (User) targetDomainObject, permissionString);
            case "Gym" -> evaluateGymPermission(user, (Gym) targetDomainObject, permissionString);
            case "Route" -> evaluateRoutePermission(user, (Route) targetDomainObject, permissionString);
            case "Post" -> evaluatePostPermission(user, (Post) targetDomainObject, permissionString);
            default -> false;
        };
    }
    
    @Override
    public boolean hasPermission(Authentication authentication, Serializable targetId, 
                                String targetType, Object permission) {
        if (authentication == null || targetId == null) {
            return false;
        }
        
        String username = authentication.getName();
        String permissionString = permission.toString().toLowerCase();
        Long id = Long.valueOf(targetId.toString());
        
        // 사용자 조회
        Optional<User> userOpt = userRepository.findByEmail(username);
        if (userOpt.isEmpty()) {
            return false;
        }
        
        User user = userOpt.get();
        
        // 관리자는 모든 권한 보유
        if (UserType.ADMIN.equals(user.getUserType())) {
            return true;
        }
        
        // ID 기반 권한 검사
        return switch (targetType.toLowerCase()) {
            case "user" -> evaluateUserPermissionById(user, id, permissionString);
            case "gym" -> evaluateGymPermissionById(user, id, permissionString);
            case "route" -> evaluateRoutePermissionById(user, id, permissionString);
            case "post" -> evaluatePostPermissionById(user, id, permissionString);
            default -> false;
        };
    }
    
    /**
     * 사용자 권한 평가
     */
    private boolean evaluateUserPermission(User currentUser, User targetUser, String permission) {
        return switch (permission) {
            case "read" -> true; // 모든 사용자 정보 조회 가능
            case "write", "update" -> currentUser.getUserId().equals(targetUser.getUserId());
            case "delete" -> currentUser.getUserId().equals(targetUser.getUserId()) || 
                            UserType.ADMIN.equals(currentUser.getUserType());
            default -> false;
        };
    }
    
    /**
     * 체육관 권한 평가
     */
    private boolean evaluateGymPermission(User currentUser, Gym gym, String permission) {
        return switch (permission) {
            case "read" -> true; // 모든 체육관 정보 조회 가능
            case "write", "update", "delete" -> {
                // 체육관 소유자이거나 관리자인 경우
                yield gym.getOwner().getUserId().equals(currentUser.getUserId()) ||
                      UserType.ADMIN.equals(currentUser.getUserType());
            }
            case "manage" -> {
                // 체육관 소유자이거나 관리자, 또는 체육관 관리자인 경우
                yield gym.getOwner().getUserId().equals(currentUser.getUserId()) ||
                      UserType.ADMIN.equals(currentUser.getUserType()) ||
                      isGymManager(currentUser, gym);
            }
            default -> false;
        };
    }
    
    /**
     * 루트 권한 평가
     */
    private boolean evaluateRoutePermission(User currentUser, Route route, String permission) {
        return switch (permission) {
            case "read" -> true; // 모든 루트 조회 가능
            case "write", "update", "delete" -> {
                // 루트 세터이거나 체육관 소유자, 관리자인 경우
                yield route.getRouteSetter().getUserId().equals(currentUser.getUserId()) ||
                      route.getBranch().getGym().getOwner().getUserId().equals(currentUser.getUserId()) ||
                      UserType.ADMIN.equals(currentUser.getUserType()) ||
                      isGymManager(currentUser, route.getBranch().getGym());
            }
            default -> false;
        };
    }
    
    /**
     * 게시글 권한 평가
     */
    private boolean evaluatePostPermission(User currentUser, Post post, String permission) {
        return switch (permission) {
            case "read" -> true; // 모든 게시글 조회 가능
            case "write" -> true; // 모든 사용자 게시글 작성 가능
            case "update", "delete" -> {
                // 게시글 작성자이거나 관리자인 경우
                yield post.getAuthor().getUserId().equals(currentUser.getUserId()) ||
                      UserType.ADMIN.equals(currentUser.getUserType());
            }
            default -> false;
        };
    }
    
    /**
     * ID 기반 사용자 권한 평가
     */
    private boolean evaluateUserPermissionById(User currentUser, Long targetUserId, String permission) {
        Optional<User> targetUserOpt = userRepository.findById(targetUserId);
        return targetUserOpt.map(targetUser -> 
            evaluateUserPermission(currentUser, targetUser, permission)).orElse(false);
    }
    
    /**
     * ID 기반 체육관 권한 평가
     */
    private boolean evaluateGymPermissionById(User currentUser, Long gymId, String permission) {
        Optional<Gym> gymOpt = gymRepository.findById(gymId);
        return gymOpt.map(gym -> 
            evaluateGymPermission(currentUser, gym, permission)).orElse(false);
    }
    
    /**
     * ID 기반 루트 권한 평가
     */
    private boolean evaluateRoutePermissionById(User currentUser, Long routeId, String permission) {
        Optional<Route> routeOpt = routeRepository.findById(routeId);
        return routeOpt.map(route -> 
            evaluateRoutePermission(currentUser, route, permission)).orElse(false);
    }
    
    /**
     * ID 기반 게시글 권한 평가
     */
    private boolean evaluatePostPermissionById(User currentUser, Long postId, String permission) {
        Optional<Post> postOpt = postRepository.findById(postId);
        return postOpt.map(post -> 
            evaluatePostPermission(currentUser, post, permission)).orElse(false);
    }
    
    /**
     * 체육관 관리자 여부 확인
     */
    private boolean isGymManager(User user, Gym gym) {
        Optional<GymMember> memberOpt = gymMemberRepository.findByUserAndGym(user, gym);
        return memberOpt.map(member -> 
            member.getMembershipType().name().contains("ADMIN") ||
            member.getMembershipType().name().contains("MANAGER")
        ).orElse(false);
    }
}
```

---

## 📋 3. CustomMethodSecurityExpressionHandler 구현

### CustomMethodSecurityExpressionHandler.java
```java
package com.routepick.security;

import org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler;
import org.springframework.security.access.expression.method.MethodSecurityExpressionOperations;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

/**
 * 커스텀 메서드 보안 표현식 핸들러
 * - SpEL 표현식 확장
 * - 커스텀 보안 메서드 추가
 */
@Component
public class CustomMethodSecurityExpressionHandler extends DefaultMethodSecurityExpressionHandler {
    
    @Override
    protected MethodSecurityExpressionOperations createSecurityExpressionRoot(
            Authentication authentication, MethodInvocation mi) {
        
        CustomMethodSecurityExpressionRoot root = 
            new CustomMethodSecurityExpressionRoot(authentication);
        root.setPermissionEvaluator(getPermissionEvaluator());
        root.setTrustResolver(getTrustResolver());
        root.setRoleHierarchy(getRoleHierarchy());
        
        return root;
    }
}
```

### CustomMethodSecurityExpressionRoot.java
```java
package com.routepick.security;

import org.springframework.security.access.expression.SecurityExpressionRoot;
import org.springframework.security.access.expression.method.MethodSecurityExpressionOperations;
import org.springframework.security.core.Authentication;

/**
 * 커스텀 보안 표현식 루트
 * - 추가 보안 메서드 제공
 * - SpEL 표현식에서 사용 가능한 커스텀 함수
 */
public class CustomMethodSecurityExpressionRoot extends SecurityExpressionRoot 
        implements MethodSecurityExpressionOperations {
    
    private Object filterObject;
    private Object returnObject;
    
    public CustomMethodSecurityExpressionRoot(Authentication authentication) {
        super(authentication);
    }
    
    /**
     * 사용자 ID 기반 권한 검사
     */
    public boolean isOwner(Long userId) {
        if (authentication == null || authentication.getName() == null) {
            return false;
        }
        
        // 현재 인증된 사용자의 ID와 비교
        try {
            String currentUserId = authentication.getName();
            return userId != null && userId.toString().equals(currentUserId);
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * 관리자 또는 소유자 권한 검사
     */
    public boolean isAdminOrOwner(Long userId) {
        return hasRole("ADMIN") || isOwner(userId);
    }
    
    /**
     * 체육관 관리자 권한 검사
     */
    public boolean isGymAdmin() {
        return hasRole("ADMIN") || hasRole("GYM_OWNER") || hasRole("GYM_ADMIN");
    }
    
    /**
     * API 제한 시간 내 여부 확인
     */
    public boolean isWithinBusinessHours() {
        java.time.LocalTime now = java.time.LocalTime.now();
        return now.isAfter(java.time.LocalTime.of(6, 0)) && 
               now.isBefore(java.time.LocalTime.of(23, 0));
    }
    
    /**
     * 한국 IP 여부 확인 (예제)
     */
    public boolean isKoreanIp() {
        // 실제 구현시 HttpServletRequest에서 IP 추출 필요
        return true; // 간단한 예제
    }
    
    @Override
    public Object getFilterObject() {
        return filterObject;
    }
    
    @Override
    public void setFilterObject(Object filterObject) {
        this.filterObject = filterObject;
    }
    
    @Override
    public Object getReturnObject() {
        return returnObject;
    }
    
    @Override
    public void setReturnObject(Object returnObject) {
        this.returnObject = returnObject;
    }
    
    @Override
    public Object getThis() {
        return this;
    }
}
```

---

## 🎯 4. 권한 기반 접근 제어 예제

### UserController.java (예제)
```java
package com.routepick.controller.api;

import com.routepick.common.ApiResponse;
import com.routepick.domain.user.entity.User;
import com.routepick.service.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.access.prepost.PostAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * 사용자 관리 API - 권한 기반 접근 제어 예제
 */
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {
    
    private final UserService userService;
    
    /**
     * 사용자 정보 조회 - 본인 또는 관리자만 접근
     */
    @GetMapping("/{userId}")
    @PreAuthorize("hasPermission(#userId, 'User', 'read') or isAdminOrOwner(#userId)")
    public ResponseEntity<ApiResponse<User>> getUser(@PathVariable Long userId) {
        User user = userService.findById(userId);
        return ResponseEntity.ok(ApiResponse.success(user));
    }
    
    /**
     * 사용자 정보 수정 - 본인만 접근
     */
    @PutMapping("/{userId}")
    @PreAuthorize("isOwner(#userId) or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<User>> updateUser(@PathVariable Long userId, 
                                                       @RequestBody UserUpdateRequest request) {
        User updatedUser = userService.updateUser(userId, request);
        return ResponseEntity.ok(ApiResponse.success(updatedUser));
    }
    
    /**
     * 사용자 삭제 - 본인 또는 관리자만 접근
     */
    @DeleteMapping("/{userId}")
    @PreAuthorize("isAdminOrOwner(#userId)")
    public ResponseEntity<ApiResponse<Void>> deleteUser(@PathVariable Long userId) {
        userService.deleteUser(userId);
        return ResponseEntity.ok(ApiResponse.success());
    }
    
    /**
     * 모든 사용자 조회 - 관리자만 접근
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<User>>> getAllUsers() {
        List<User> users = userService.findAll();
        return ResponseEntity.ok(ApiResponse.success(users));
    }
    
    /**
     * 사용자 프로필 조회 - 반환 후 검증
     */
    @GetMapping("/{userId}/profile")
    @PostAuthorize("hasPermission(returnObject.body.data, 'read')")
    public ResponseEntity<ApiResponse<UserProfile>> getUserProfile(@PathVariable Long userId) {
        UserProfile profile = userService.getUserProfile(userId);
        return ResponseEntity.ok(ApiResponse.success(profile));
    }
}
```

### GymController.java (예제)
```java
package com.routepick.controller.api;

import com.routepick.common.ApiResponse;
import com.routepick.domain.gym.entity.Gym;
import com.routepick.service.gym.GymService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * 체육관 관리 API - 권한 기반 접근 제어 예제
 */
@RestController
@RequestMapping("/api/v1/gyms")
@RequiredArgsConstructor
public class GymController {
    
    private final GymService gymService;
    
    /**
     * 체육관 생성 - 체육관 사업자만 가능
     */
    @PostMapping
    @PreAuthorize("hasRole('GYM_OWNER') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Gym>> createGym(@RequestBody GymCreateRequest request) {
        Gym gym = gymService.createGym(request);
        return ResponseEntity.ok(ApiResponse.success(gym));
    }
    
    /**
     * 체육관 수정 - 체육관 소유자 또는 관리자만 가능
     */
    @PutMapping("/{gymId}")
    @PreAuthorize("hasPermission(#gymId, 'Gym', 'update')")
    public ResponseEntity<ApiResponse<Gym>> updateGym(@PathVariable Long gymId,
                                                     @RequestBody GymUpdateRequest request) {
        Gym updatedGym = gymService.updateGym(gymId, request);
        return ResponseEntity.ok(ApiResponse.success(updatedGym));
    }
    
    /**
     * 체육관 삭제 - 체육관 소유자 또는 관리자만 가능
     */
    @DeleteMapping("/{gymId}")
    @PreAuthorize("hasPermission(#gymId, 'Gym', 'delete')")
    public ResponseEntity<ApiResponse<Void>> deleteGym(@PathVariable Long gymId) {
        gymService.deleteGym(gymId);
        return ResponseEntity.ok(ApiResponse.success());
    }
    
    /**
     * 체육관 관리 - 소유자, 관리자, 체육관 매니저만 접근
     */
    @GetMapping("/{gymId}/management")
    @PreAuthorize("hasPermission(#gymId, 'Gym', 'manage')")
    public ResponseEntity<ApiResponse<GymManagementDto>> getGymManagement(@PathVariable Long gymId) {
        GymManagementDto management = gymService.getGymManagement(gymId);
        return ResponseEntity.ok(ApiResponse.success(management));
    }
    
    /**
     * 영업시간 내 체육관 정보 수정
     */
    @PutMapping("/{gymId}/hours")
    @PreAuthorize("hasPermission(#gymId, 'Gym', 'update') and isWithinBusinessHours()")
    public ResponseEntity<ApiResponse<Void>> updateBusinessHours(@PathVariable Long gymId,
                                                               @RequestBody BusinessHoursRequest request) {
        gymService.updateBusinessHours(gymId, request);
        return ResponseEntity.ok(ApiResponse.success());
    }
}
```

---

## 📊 5. 권한 감사 및 로깅

### SecurityAuditLogger.java
```java
package com.routepick.service.security;

import com.routepick.service.system.ApiLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.security.access.event.AuthorizationFailureEvent;
import org.springframework.security.access.event.AuthorizedEvent;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/**
 * 보안 감사 로거
 * - 권한 검사 이벤트 로깅
 * - 보안 감사 추적
 * - 접근 실패 모니터링
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SecurityAuditLogger {
    
    private final ApiLogService apiLogService;
    
    /**
     * 권한 부여 성공 이벤트
     */
    @EventListener
    public void onAuthorizationSuccess(AuthorizedEvent event) {
        Authentication auth = (Authentication) event.getAuthentication();
        String username = auth != null ? auth.getName() : "anonymous";
        
        log.debug("Authorization successful: user={}, resource={}", 
                username, event.getSource());
    }
    
    /**
     * 권한 부여 실패 이벤트
     */
    @EventListener
    public void onAuthorizationFailure(AuthorizationFailureEvent event) {
        Authentication auth = event.getAuthentication();
        String username = auth != null ? auth.getName() : "anonymous";
        
        log.warn("Authorization failed: user={}, resource={}, reason={}", 
                username, event.getSource(), event.getAccessDeniedException().getMessage());
        
        // API 로그에 기록
        try {
            apiLogService.logError(
                "SECURITY_AUTHORIZATION",
                "ACCESS_DENIED",
                String.format("User '%s' denied access to resource", username),
                "SecurityAuditLogger"
            );
        } catch (Exception e) {
            log.error("Failed to log authorization failure", e);
        }
    }
    
    /**
     * 권한 검사 통계
     */
    public void logPermissionCheck(String username, String resource, 
                                  String permission, boolean granted) {
        String message = String.format(
            "Permission check: user=%s, resource=%s, permission=%s, granted=%s",
            username, resource, permission, granted
        );
        
        if (granted) {
            log.debug(message);
        } else {
            log.warn(message);
        }
    }
}
```

---

## ⚙️ 6. 역할 계층 설정

### RoleHierarchyConfig.java
```java
package com.routepick.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;

/**
 * 역할 계층 설정
 * - ADMIN > GYM_OWNER > USER
 * - 상위 역할이 하위 역할 권한 포함
 */
@Configuration
public class RoleHierarchyConfig {
    
    @Bean
    public RoleHierarchy roleHierarchy() {
        RoleHierarchyImpl hierarchy = new RoleHierarchyImpl();
        hierarchy.setHierarchy(
            "ROLE_ADMIN > ROLE_GYM_OWNER " +
            "ROLE_GYM_OWNER > ROLE_USER"
        );
        return hierarchy;
    }
}
```

---

## 📋 7. 권한 체계 정리

### 권한 매트릭스

| 리소스 | USER | GYM_OWNER | ADMIN | 설명 |
|--------|------|-----------|-------|------|
| **User Profile** | 본인만 | 본인만 | 모든 사용자 | 개인정보 보호 |
| **Gym Management** | 조회만 | 소유 체육관만 | 모든 체육관 | 체육관 운영 |
| **Route Setting** | 조회만 | 소유 체육관 루트 | 모든 루트 | 루트 관리 |
| **Community Post** | 본인 게시글 | 본인 게시글 | 모든 게시글 | 커뮤니티 관리 |
| **System Admin** | 접근 불가 | 접근 불가 | 전체 접근 | 시스템 관리 |

### SpEL 표현식 예제

```java
// 기본 역할 검사
@PreAuthorize("hasRole('ADMIN')")

// 소유권 검사
@PreAuthorize("isOwner(#userId)")

// 복합 조건
@PreAuthorize("hasRole('ADMIN') or isOwner(#userId)")

// 커스텀 권한 검사
@PreAuthorize("hasPermission(#gymId, 'Gym', 'manage')")

// 시간 기반 접근 제어
@PreAuthorize("hasRole('GYM_OWNER') and isWithinBusinessHours()")

// 반환 값 기반 검증
@PostAuthorize("hasPermission(returnObject, 'read')")
```

---

## 📈 8. 사용 가이드

### Controller 메서드 보안 적용
```java
@RestController
public class ExampleController {
    
    // 1. 단순 역할 검사
    @GetMapping("/admin/users")
    @PreAuthorize("hasRole('ADMIN')")
    public List<User> getAllUsers() { ... }
    
    // 2. 소유권 검사
    @PutMapping("/users/{id}")
    @PreAuthorize("isOwner(#id) or hasRole('ADMIN')")
    public User updateUser(@PathVariable Long id, ...) { ... }
    
    // 3. 커스텀 권한 평가
    @DeleteMapping("/gyms/{gymId}")
    @PreAuthorize("hasPermission(#gymId, 'Gym', 'delete')")
    public void deleteGym(@PathVariable Long gymId) { ... }
    
    // 4. 복잡한 조건
    @PostMapping("/routes")
    @PreAuthorize("isGymAdmin() and isWithinBusinessHours()")
    public Route createRoute(...) { ... }
}
```

---

*Step 8-2c 완료: 메서드 보안 설정 구현 (권한 기반 접근 제어)*
*다음 파일: step8-2d_security_monitoring.md*