# Step 7-2h: 조건부 데이터 마스킹 구현

## 📋 구현 목표
사용자 관계와 권한에 따라 민감정보를 선택적으로 노출/마스킹하는 고급 보안 시스템:
1. **권한별 데이터 노출 제어** - 본인/팔로워/타인별 차등 정보 제공
2. **동적 마스킹** - 런타임에 권한 체크하여 마스킹 적용
3. **커스텀 직렬화** - Jackson Serializer를 활용한 투명한 처리
4. **AOP 기반 접근 제어** - 메서드 레벨 권한 체크

---

## 🎭 ConditionalMaskingSerializer 구현

### 📁 파일 위치
```
src/main/java/com/routepick/security/serializer/ConditionalMaskingSerializer.java
```

### 📝 구현 코드
```java
package com.routepick.security.serializer;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.routepick.security.context.SecurityContextHolder;
import com.routepick.security.masking.MaskingRule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.lang.reflect.Field;

/**
 * 조건부 데이터 마스킹 Serializer
 * 
 * @author RoutePickProj
 * @since 1.0
 */
@Slf4j
@Component
public class ConditionalMaskingSerializer extends JsonSerializer<String> {

    @Override
    public void serialize(String value, JsonGenerator gen, SerializerProvider serializers) 
            throws IOException {
        
        if (value == null) {
            gen.writeNull();
            return;
        }

        // 현재 직렬화 중인 필드 정보 가져오기
        String fieldName = gen.getOutputContext().getCurrentName();
        Object currentObject = gen.getCurrentValue();
        
        if (currentObject == null) {
            gen.writeString(value);
            return;
        }

        try {
            // 필드에 적용된 마스킹 규칙 조회
            Field field = findFieldByName(currentObject.getClass(), fieldName);
            if (field == null) {
                gen.writeString(value);
                return;
            }

            MaskingRule maskingRule = field.getAnnotation(MaskingRule.class);
            if (maskingRule == null) {
                gen.writeString(value);
                return;
            }

            // 마스킹 조건 평가
            boolean shouldMask = evaluateMaskingCondition(maskingRule, currentObject);
            
            if (shouldMask) {
                String maskedValue = applyMasking(value, maskingRule);
                gen.writeString(maskedValue);
                log.debug("Field masked: field={}, original length={}, masked length={}", 
                         fieldName, value.length(), maskedValue.length());
            } else {
                gen.writeString(value);
                log.debug("Field not masked: field={}", fieldName);
            }
            
        } catch (Exception e) {
            log.error("Error during conditional masking for field: {}", fieldName, e);
            // 에러 시 보안을 위해 전체 마스킹
            gen.writeString("***");
        }
    }

    /**
     * 클래스에서 필드 찾기 (상속 고려)
     */
    private Field findFieldByName(Class<?> clazz, String fieldName) {
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            try {
                Field field = current.getDeclaredField(fieldName);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException e) {
                current = current.getSuperclass();
            }
        }
        return null;
    }

    /**
     * 마스킹 조건 평가
     */
    private boolean evaluateMaskingCondition(MaskingRule rule, Object currentObject) {
        String condition = rule.condition();
        
        // 기본 조건들 처리
        if ("ALWAYS".equals(condition)) {
            return true;
        }
        
        if ("NEVER".equals(condition)) {
            return false;
        }

        // 사용자 관계 기반 조건 평가
        return evaluateUserRelationCondition(condition, currentObject);
    }

    /**
     * 사용자 관계 기반 조건 평가
     */
    private boolean evaluateUserRelationCondition(String condition, Object currentObject) {
        // 현재 요청 컨텍스트에서 정보 추출
        Long currentUserId = SecurityContextHolder.getCurrentUserId();
        Long targetUserId = extractTargetUserId(currentObject);
        
        if (currentUserId == null || targetUserId == null) {
            return true; // 정보가 없으면 보수적으로 마스킹
        }

        // 본인 여부 체크
        boolean isSameUser = currentUserId.equals(targetUserId);
        
        // 팔로우 관계 체크
        boolean isFollower = SecurityContextHolder.isFollowing(currentUserId, targetUserId);
        
        // 조건 평가
        return switch (condition) {
            case "!isSameUser" -> !isSameUser;
            case "!isSameUser && !isFollower" -> !isSameUser && !isFollower;
            case "!isFollower" -> !isFollower;
            case "isPrivateProfile" -> isPrivateProfile(targetUserId);
            default -> evaluateCustomCondition(condition, isSameUser, isFollower);
        };
    }

    /**
     * 대상 사용자 ID 추출
     */
    private Long extractTargetUserId(Object currentObject) {
        try {
            // userId 필드 찾기
            Field userIdField = findFieldByName(currentObject.getClass(), "userId");
            if (userIdField != null) {
                Object value = userIdField.get(currentObject);
                return value instanceof Long ? (Long) value : null;
            }
            
            // targetUserId 필드 찾기
            Field targetUserIdField = findFieldByName(currentObject.getClass(), "targetUserId");
            if (targetUserIdField != null) {
                Object value = targetUserIdField.get(currentObject);
                return value instanceof Long ? (Long) value : null;
            }
            
        } catch (Exception e) {
            log.warn("Failed to extract target user ID from object: {}", currentObject.getClass().getSimpleName());
        }
        
        return null;
    }

    /**
     * 비공개 프로필 여부 체크
     */
    private boolean isPrivateProfile(Long userId) {
        // TODO: UserProfileService를 통해 프로필 공개 설정 조회
        return false;
    }

    /**
     * 커스텀 조건 평가
     */
    private boolean evaluateCustomCondition(String condition, boolean isSameUser, boolean isFollower) {
        // SpEL(Spring Expression Language) 또는 커스텀 평가기 사용 가능
        log.debug("Evaluating custom condition: {}, same={}, follower={}", 
                 condition, isSameUser, isFollower);
        
        // 현재는 기본값으로 마스킹
        return true;
    }

    /**
     * 마스킹 적용
     */
    private String applyMasking(String value, MaskingRule rule) {
        String pattern = rule.maskingPattern();
        MaskingRule.MaskingType type = rule.type();
        
        return switch (type) {
            case EMAIL -> maskEmail(value);
            case PHONE -> maskPhoneNumber(value);
            case NAME -> maskName(value);
            case CUSTOM -> maskCustom(value, pattern);
            case FULL -> "***";
            default -> value;
        };
    }

    /**
     * 이메일 마스킹
     */
    private String maskEmail(String email) {
        if (email == null || !email.contains("@")) {
            return "***";
        }
        
        int atIndex = email.indexOf('@');
        String username = email.substring(0, atIndex);
        String domain = email.substring(atIndex);
        
        if (username.length() <= 1) {
            return "***" + domain;
        }
        
        return username.charAt(0) + "***" + domain;
    }

    /**
     * 휴대폰 번호 마스킹
     */
    private String maskPhoneNumber(String phone) {
        if (phone == null) {
            return null;
        }
        
        if (phone.matches("^010-\\d{4}-\\d{4}$")) {
            return phone.substring(0, 4) + "****" + phone.substring(8);
        }
        
        return "010-****-****";
    }

    /**
     * 이름 마스킹
     */
    private String maskName(String name) {
        if (name == null || name.length() <= 1) {
            return "***";
        }
        
        if (name.length() == 2) {
            return name.charAt(0) + "*";
        }
        
        return name.charAt(0) + "*".repeat(name.length() - 2) + name.charAt(name.length() - 1);
    }

    /**
     * 커스텀 패턴 마스킹
     */
    private String maskCustom(String value, String pattern) {
        if ("***".equals(pattern)) {
            return "***";
        }
        
        if (pattern.startsWith("keep:") && pattern.length() > 5) {
            int keepLength = Integer.parseInt(pattern.substring(5));
            if (value.length() <= keepLength) {
                return value;
            }
            return value.substring(0, keepLength) + "***";
        }
        
        return pattern;
    }
}
```

---

## 🏷️ MaskingRule Annotation

### 📁 파일 위치
```
src/main/java/com/routepick/security/masking/MaskingRule.java
```

### 📝 구현 코드
```java
package com.routepick.security.masking;

import com.fasterxml.jackson.annotation.JacksonAnnotationsInside;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.routepick.security.serializer.ConditionalMaskingSerializer;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 조건부 마스킹 규칙 어노테이션
 * 
 * @author RoutePickProj
 * @since 1.0
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@JacksonAnnotationsInside
@JsonSerialize(using = ConditionalMaskingSerializer.class)
public @interface MaskingRule {
    
    /**
     * 마스킹 조건
     * - ALWAYS: 항상 마스킹
     * - NEVER: 마스킹 안함
     * - !isSameUser: 본인이 아닐 때 마스킹
     * - !isSameUser && !isFollower: 본인도 아니고 팔로워도 아닐 때
     * - !isFollower: 팔로워가 아닐 때
     * - isPrivateProfile: 비공개 프로필일 때
     */
    String condition() default "!isSameUser";
    
    /**
     * 마스킹 타입
     */
    MaskingType type() default MaskingType.CUSTOM;
    
    /**
     * 커스텀 마스킹 패턴
     */
    String maskingPattern() default "***";
    
    enum MaskingType {
        EMAIL,      // 이메일 마스킹
        PHONE,      // 휴대폰 마스킹  
        NAME,       // 이름 마스킹
        CUSTOM,     // 커스텀 패턴
        FULL        // 전체 마스킹
    }
}
```

---

## 🛡️ SecurityContextHolder 확장

### 📁 파일 위치
```
src/main/java/com/routepick/security/context/SecurityContextHolder.java
```

### 📝 구현 코드
```java
package com.routepick.security.context;

import com.routepick.service.user.FollowService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.stereotype.Component;

/**
 * 보안 컨텍스트 확장 유틸리티
 * 
 * @author RoutePickProj
 * @since 1.0
 */
@Slf4j
@Component
public class SecurityContextHolder {
    
    private static FollowService followService;
    
    @Autowired
    public void setFollowService(FollowService followService) {
        SecurityContextHolder.followService = followService;
    }

    /**
     * 현재 사용자 ID 조회
     */
    public static Long getCurrentUserId() {
        SecurityContext context = org.springframework.security.core.context.SecurityContextHolder.getContext();
        Authentication authentication = context.getAuthentication();
        
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        
        Object principal = authentication.getPrincipal();
        if (principal instanceof Long) {
            return (Long) principal;
        }
        
        if (principal instanceof String) {
            try {
                return Long.parseLong((String) principal);
            } catch (NumberFormatException e) {
                log.warn("Failed to parse user ID from principal: {}", principal);
                return null;
            }
        }
        
        return null;
    }

    /**
     * 팔로우 관계 확인
     */
    public static boolean isFollowing(Long followerId, Long followingId) {
        if (followerId == null || followingId == null || followService == null) {
            return false;
        }
        
        try {
            return followService.isFollowing(followerId, followingId);
        } catch (Exception e) {
            log.warn("Failed to check follow relationship: follower={}, following={}", 
                    followerId, followingId, e);
            return false;
        }
    }

    /**
     * 현재 사용자가 대상 사용자를 팔로우하는지 확인
     */
    public static boolean isCurrentUserFollowing(Long targetUserId) {
        Long currentUserId = getCurrentUserId();
        if (currentUserId == null || targetUserId == null) {
            return false;
        }
        
        return isFollowing(currentUserId, targetUserId);
    }

    /**
     * 현재 사용자가 대상 사용자와 동일한지 확인
     */
    public static boolean isSameUser(Long targetUserId) {
        Long currentUserId = getCurrentUserId();
        return currentUserId != null && currentUserId.equals(targetUserId);
    }
}
```

---

## 📱 Response DTO 수정 예시

### 📝 UserProfileResponse 조건부 마스킹 적용
```java
package com.routepick.dto.user.response;

import com.routepick.security.masking.MaskingRule;
import com.routepick.security.masking.MaskingRule.MaskingType;

/**
 * 조건부 마스킹이 적용된 UserProfileResponse
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "사용자 프로필 정보 응답")
public class UserProfileResponse {

    @Schema(description = "사용자 ID", example = "123")
    @JsonProperty("userId")
    private Long userId;

    @Schema(description = "이메일", example = "user***@example.com")
    @MaskingRule(condition = "!isSameUser", type = MaskingType.EMAIL)
    @JsonProperty("email")
    private String email;

    @Schema(description = "실명", example = "김철수")
    @MaskingRule(condition = "!isSameUser", type = MaskingType.NAME)
    @JsonProperty("realName")
    private String realName;

    @Schema(description = "휴대폰 번호", example = "010-****-5678")
    @MaskingRule(condition = "!isSameUser && !isFollower", type = MaskingType.PHONE)
    @JsonProperty("phoneNumber")
    private String phoneNumber;

    @Schema(description = "생년월일", example = "1995-03-15")
    @MaskingRule(condition = "isPrivateProfile", maskingPattern = "***")
    @JsonProperty("birthDate")
    private LocalDate birthDate;

    // 나머지 필드들...
}
```

---

## 🔍 ProfileVisibilityAspect 구현

### 📁 파일 위치
```
src/main/java/com/routepick/security/aspect/ProfileVisibilityAspect.java
```

### 📝 구현 코드
```java
package com.routepick.security.aspect;

import com.routepick.exception.user.ProfileAccessDeniedException;
import com.routepick.security.annotation.ProfileAccessControl;
import com.routepick.security.context.SecurityContextHolder;
import com.routepick.service.user.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

/**
 * 프로필 접근 제어 AOP
 * 
 * @author RoutePickProj
 * @since 1.0
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class ProfileVisibilityAspect {

    private final UserService userService;

    @Around("@annotation(profileAccessControl)")
    public Object checkProfileAccess(ProceedingJoinPoint joinPoint, 
                                   ProfileAccessControl profileAccessControl) throws Throwable {
        
        Long currentUserId = SecurityContextHolder.getCurrentUserId();
        Long targetUserId = extractTargetUserId(joinPoint);
        
        if (targetUserId == null) {
            log.warn("Cannot extract target user ID from method arguments");
            return joinPoint.proceed();
        }
        
        if (currentUserId == null) {
            throw new ProfileAccessDeniedException("인증이 필요합니다");
        }
        
        // 접근 권한 체크
        if (!canAccessProfile(currentUserId, targetUserId, profileAccessControl.level())) {
            throw new ProfileAccessDeniedException("프로필 접근 권한이 없습니다");
        }
        
        log.debug("Profile access granted: current={}, target={}", currentUserId, targetUserId);
        
        return joinPoint.proceed();
    }

    /**
     * 메서드 인자에서 대상 사용자 ID 추출
     */
    private Long extractTargetUserId(ProceedingJoinPoint joinPoint) {
        Object[] args = joinPoint.getArgs();
        
        // 첫 번째 Long 타입 인자를 대상 사용자 ID로 가정
        for (Object arg : args) {
            if (arg instanceof Long) {
                return (Long) arg;
            }
        }
        
        return null;
    }

    /**
     * 프로필 접근 권한 체크
     */
    private boolean canAccessProfile(Long currentUserId, Long targetUserId, String accessLevel) {
        // 본인은 항상 접근 가능
        if (currentUserId.equals(targetUserId)) {
            return true;
        }
        
        return switch (accessLevel) {
            case "PUBLIC" -> true;
            case "FOLLOWERS" -> SecurityContextHolder.isFollowing(currentUserId, targetUserId);
            case "PRIVATE" -> false;
            case "SAME_USER" -> currentUserId.equals(targetUserId);
            default -> false;
        };
    }
}
```

---

## 🏷️ ProfileAccessControl Annotation

### 📁 파일 위치
```
src/main/java/com/routepick/security/annotation/ProfileAccessControl.java
```

### 📝 구현 코드
```java
package com.routepick.security.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 프로필 접근 제어 어노테이션
 * 
 * @author RoutePickProj
 * @since 1.0
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ProfileAccessControl {
    
    /**
     * 접근 레벨
     * - PUBLIC: 누구나 접근 가능
     * - FOLLOWERS: 팔로워만 접근 가능  
     * - PRIVATE: 본인만 접근 가능
     * - SAME_USER: 본인만 접근 가능 (PRIVATE와 동일)
     */
    String level() default "FOLLOWERS";
    
    /**
     * 접근 거부 시 메시지
     */
    String message() default "프로필 접근 권한이 없습니다";
}
```

---

## 📋 구현 완료 사항
✅ **ConditionalMaskingSerializer** - 동적 마스킹 처리  
✅ **MaskingRule 어노테이션** - 선언적 마스킹 규칙  
✅ **SecurityContextHolder 확장** - 사용자 관계 체크  
✅ **ProfileVisibilityAspect** - AOP 기반 접근 제어  
✅ **ProfileAccessControl 어노테이션** - 메서드 레벨 보안  

## 🎯 주요 특징
- **선언적 마스킹** - 어노테이션으로 간단한 설정
- **동적 조건 평가** - 런타임에 사용자 관계 확인
- **투명한 처리** - Jackson 직렬화 과정에 자연스럽게 통합
- **성능 최적화** - 필요한 경우만 관계 체크
- **확장 가능** - 새로운 조건과 마스킹 타입 쉽게 추가

## 🔧 사용 예시
```java
// Controller에서 사용
@GetMapping("/{userId}/profile")
@ProfileAccessControl(level = "FOLLOWERS")
public ResponseEntity<UserProfileResponse> getUserProfile(@PathVariable Long userId) {
    // 자동으로 접근 권한 체크 및 데이터 마스킹 적용
    return ResponseEntity.ok(userProfileService.getUserProfile(userId));
}
```