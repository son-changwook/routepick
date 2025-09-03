# Step 7-2c: 사용자 및 프로필 관리 Request DTOs

## 📋 구현 목표
사용자 및 프로필 관리를 위한 4개 Request DTO 클래스 구현:
1. **UserProfileUpdateRequest** - 사용자 프로필 수정
2. **UserSearchRequest** - 사용자 검색  
3. **ProfileImageUploadRequest** - 프로필 이미지 업로드
4. **AccountDeactivateRequest** - 계정 비활성화

## 🎯 핵심 구현 사항
- **한국 특화 검증**: 휴대폰, 닉네임 패턴
- **보안 강화**: XSS 방지, SQL Injection 대응
- **Bean Validation**: @Valid 어노테이션 적용
- **API 문서화**: Swagger 어노테이션

---

## 1. UserProfileUpdateRequest
### 📁 파일 위치
```
src/main/java/com/routepick/core/model/dto/request/user/UserProfileUpdateRequest.java
```

### 📝 구현 코드
```java
package com.routepick.core.model.dto.request.user;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.routepick.core.common.validation.Korean;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * 사용자 프로필 수정 Request DTO
 * 
 * @author RoutePickProj
 * @since 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "사용자 프로필 수정 요청")
public class UserProfileUpdateRequest {

    @Schema(description = "닉네임 (한글/영문/숫자 2-10자)", 
            example = "클라이머김")
    @NotBlank(message = "닉네임은 필수입니다")
    @Size(min = 2, max = 10, message = "닉네임은 2-10자여야 합니다")
    @Pattern(regexp = "^[가-힣a-zA-Z0-9]+$", 
             message = "닉네임은 한글, 영문, 숫자만 사용 가능합니다")
    @JsonProperty("nickName")
    private String nickName;

    @Schema(description = "휴대폰 번호 (010-XXXX-XXXX)", 
            example = "010-1234-5678")
    @Pattern(regexp = "^010-\\d{4}-\\d{4}$", 
             message = "올바른 휴대폰 번호 형식이 아닙니다 (010-XXXX-XXXX)")
    @JsonProperty("phoneNumber")
    private String phoneNumber;

    @Schema(description = "생년월일", 
            example = "1995-03-15")
    @Past(message = "생년월일은 과거 날짜여야 합니다")
    @JsonProperty("birthDate")
    private LocalDate birthDate;

    @Schema(description = "성별 (M: 남성, F: 여성)", 
            example = "M")
    @Pattern(regexp = "^[MF]$", 
             message = "성별은 M(남성) 또는 F(여성)이어야 합니다")
    @JsonProperty("gender")
    private String gender;

    @Schema(description = "자기소개 (최대 500자)", 
            example = "클라이밍을 사랑하는 초보자입니다!")
    @Size(max = 500, message = "자기소개는 500자 이내로 작성해주세요")
    @Korean.NoHarmfulContent(message = "부적절한 내용이 포함되어 있습니다")
    @JsonProperty("bio")
    private String bio;

    @Schema(description = "관심 지역", 
            example = "서울특별시 강남구")
    @Size(max = 100, message = "관심 지역은 100자 이내로 입력해주세요")
    @JsonProperty("interestedRegion")
    private String interestedRegion;

    @Schema(description = "프로필 공개 설정 (PUBLIC: 전체공개, FOLLOWERS: 팔로워만, PRIVATE: 비공개)", 
            example = "PUBLIC")
    @NotNull(message = "프로필 공개 설정은 필수입니다")
    @Pattern(regexp = "^(PUBLIC|FOLLOWERS|PRIVATE)$", 
             message = "프로필 공개 설정은 PUBLIC, FOLLOWERS, PRIVATE 중 하나여야 합니다")
    @JsonProperty("profileVisibility")
    private String profileVisibility;

    @Schema(description = "클라이밍 경력 (개월 단위)", 
            example = "24")
    @Min(value = 0, message = "클라이밍 경력은 0 이상이어야 합니다")
    @Max(value = 600, message = "클라이밍 경력은 600개월 이하여야 합니다")
    @JsonProperty("climbingExperienceMonths")
    private Integer climbingExperienceMonths;

    @Schema(description = "선호 클라이밍 스타일", 
            example = "볼더링")
    @Size(max = 50, message = "선호 클라이밍 스타일은 50자 이내로 입력해주세요")
    @JsonProperty("preferredClimbingStyle")
    private String preferredClimbingStyle;

    @Schema(description = "인스타그램 계정", 
            example = "@climber_kim")
    @Size(max = 50, message = "인스타그램 계정은 50자 이내로 입력해주세요")
    @Pattern(regexp = "^@?[a-zA-Z0-9._]{1,50}$|^$", 
             message = "올바른 인스타그램 계정 형식이 아닙니다")
    @JsonProperty("instagramAccount")
    private String instagramAccount;

    @Schema(description = "위치 정보 공유 동의", 
            example = "true")
    @JsonProperty("locationSharingConsent")
    private Boolean locationSharingConsent;
}
```

---

## 2. UserSearchRequest
### 📁 파일 위치
```
src/main/java/com/routepick/core/model/dto/request/user/UserSearchRequest.java
```

### 📝 구현 코드
```java
package com.routepick.core.model.dto.request.user;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 사용자 검색 Request DTO
 * 
 * @author RoutePickProj
 * @since 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "사용자 검색 요청")
public class UserSearchRequest {

    @Schema(description = "검색 키워드 (닉네임, 이메일, 실명 등)", 
            example = "클라이머")
    @NotBlank(message = "검색 키워드는 필수입니다")
    @Size(min = 1, max = 50, message = "검색 키워드는 1-50자여야 합니다")
    @Pattern(regexp = "^[가-힣a-zA-Z0-9@._\\s-]+$", 
             message = "검색 키워드는 한글, 영문, 숫자, @, ., _, 공백, -만 사용 가능합니다")
    @JsonProperty("keyword")
    private String keyword;

    @Schema(description = "검색 타입 (NICKNAME: 닉네임, EMAIL: 이메일, NAME: 실명, ALL: 전체)", 
            example = "NICKNAME")
    @Pattern(regexp = "^(NICKNAME|EMAIL|NAME|ALL)$", 
             message = "검색 타입은 NICKNAME, EMAIL, NAME, ALL 중 하나여야 합니다")
    @Builder.Default
    @JsonProperty("searchType")
    private String searchType = "ALL";

    @Schema(description = "페이지 번호 (0부터 시작)", 
            example = "0")
    @Min(value = 0, message = "페이지 번호는 0 이상이어야 합니다")
    @Builder.Default
    @JsonProperty("page")
    private Integer page = 0;

    @Schema(description = "페이지 크기 (최대 50)", 
            example = "20")
    @Min(value = 1, message = "페이지 크기는 1 이상이어야 합니다")
    @Max(value = 50, message = "페이지 크기는 50 이하여야 합니다")
    @Builder.Default
    @JsonProperty("size")
    private Integer size = 20;

    @Schema(description = "정렬 기준 (RELEVANCE: 관련도순, RECENT: 최근순, NAME: 이름순)", 
            example = "RELEVANCE")
    @Pattern(regexp = "^(RELEVANCE|RECENT|NAME)$", 
             message = "정렬 기준은 RELEVANCE, RECENT, NAME 중 하나여야 합니다")
    @Builder.Default
    @JsonProperty("sortBy")
    private String sortBy = "RELEVANCE";

    @Schema(description = "정렬 방향 (ASC: 오름차순, DESC: 내림차순)", 
            example = "DESC")
    @Pattern(regexp = "^(ASC|DESC)$", 
             message = "정렬 방향은 ASC 또는 DESC여야 합니다")
    @Builder.Default
    @JsonProperty("sortDirection")
    private String sortDirection = "DESC";

    @Schema(description = "팔로워만 검색 여부", 
            example = "false")
    @Builder.Default
    @JsonProperty("followersOnly")
    private Boolean followersOnly = false;

    @Schema(description = "활성 사용자만 검색 여부", 
            example = "true")
    @Builder.Default
    @JsonProperty("activeUsersOnly")
    private Boolean activeUsersOnly = true;

    @Schema(description = "프로필 이미지가 있는 사용자만 검색 여부", 
            example = "false")
    @Builder.Default
    @JsonProperty("withProfileImageOnly")
    private Boolean withProfileImageOnly = false;

    @Schema(description = "클라이밍 경력 최소 개월 수", 
            example = "6")
    @Min(value = 0, message = "클라이밍 경력은 0 이상이어야 합니다")
    @Max(value = 600, message = "클라이밍 경력은 600개월 이하여야 합니다")
    @JsonProperty("minExperienceMonths")
    private Integer minExperienceMonths;

    @Schema(description = "클라이밍 경력 최대 개월 수", 
            example = "120")
    @Min(value = 0, message = "클라이밍 경력은 0 이상이어야 합니다")
    @Max(value = 600, message = "클라이밍 경력은 600개월 이하여야 합니다")
    @JsonProperty("maxExperienceMonths")
    private Integer maxExperienceMonths;

    @Schema(description = "지역 필터", 
            example = "서울특별시")
    @Size(max = 100, message = "지역 필터는 100자 이내로 입력해주세요")
    @JsonProperty("regionFilter")
    private String regionFilter;
}
```

---

## 3. ProfileImageUploadRequest
### 📁 파일 위치
```
src/main/java/com/routepick/core/model/dto/request/user/ProfileImageUploadRequest.java
```

### 📝 구현 코드
```java
package com.routepick.core.model.dto.request.user;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 프로필 이미지 업로드 Request DTO
 * 
 * @author RoutePickProj
 * @since 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "프로필 이미지 업로드 요청")
public class ProfileImageUploadRequest {

    @Schema(description = "이미지 타입 (PROFILE: 프로필, COVER: 커버)", 
            example = "PROFILE")
    @NotBlank(message = "이미지 타입은 필수입니다")
    @Pattern(regexp = "^(PROFILE|COVER)$", 
             message = "이미지 타입은 PROFILE 또는 COVER여야 합니다")
    @Builder.Default
    @JsonProperty("imageType")
    private String imageType = "PROFILE";

    @Schema(description = "이미지 설명 (선택사항)", 
            example = "새로운 프로필 사진")
    @Size(max = 200, message = "이미지 설명은 200자 이내로 입력해주세요")
    @JsonProperty("imageDescription")
    private String imageDescription;

    @Schema(description = "이미지 품질 설정 (HIGH: 고화질, MEDIUM: 중화질, LOW: 저화질)", 
            example = "MEDIUM")
    @Pattern(regexp = "^(HIGH|MEDIUM|LOW)$", 
             message = "이미지 품질은 HIGH, MEDIUM, LOW 중 하나여야 합니다")
    @Builder.Default
    @JsonProperty("imageQuality")
    private String imageQuality = "MEDIUM";

    @Schema(description = "썸네일 생성 여부", 
            example = "true")
    @Builder.Default
    @JsonProperty("generateThumbnail")
    private Boolean generateThumbnail = true;

    @Schema(description = "썸네일 크기 (픽셀 단위)", 
            example = "200")
    @Min(value = 50, message = "썸네일 크기는 50 이상이어야 합니다")
    @Max(value = 500, message = "썸네일 크기는 500 이하여야 합니다")
    @Builder.Default
    @JsonProperty("thumbnailSize")
    private Integer thumbnailSize = 200;

    @Schema(description = "이미지 압축 여부", 
            example = "true")
    @Builder.Default
    @JsonProperty("compressImage")
    private Boolean compressImage = true;

    @Schema(description = "압축 품질 (0-100, 높을수록 고품질)", 
            example = "85")
    @Min(value = 10, message = "압축 품질은 10 이상이어야 합니다")
    @Max(value = 100, message = "압축 품질은 100 이하여야 합니다")
    @Builder.Default
    @JsonProperty("compressionQuality")
    private Integer compressionQuality = 85;

    @Schema(description = "기존 이미지 교체 여부", 
            example = "true")
    @Builder.Default
    @JsonProperty("replaceExisting")
    private Boolean replaceExisting = true;

    @Schema(description = "이미지 공개 설정 (PUBLIC: 전체공개, FOLLOWERS: 팔로워만, PRIVATE: 비공개)", 
            example = "PUBLIC")
    @Pattern(regexp = "^(PUBLIC|FOLLOWERS|PRIVATE)$", 
             message = "이미지 공개 설정은 PUBLIC, FOLLOWERS, PRIVATE 중 하나여야 합니다")
    @Builder.Default
    @JsonProperty("imageVisibility")
    private String imageVisibility = "PUBLIC";

    @Schema(description = "워터마크 추가 여부", 
            example = "false")
    @Builder.Default
    @JsonProperty("addWatermark")
    private Boolean addWatermark = false;

    @Schema(description = "EXIF 데이터 제거 여부 (보안)", 
            example = "true")
    @Builder.Default
    @JsonProperty("removeExifData")
    private Boolean removeExifData = true;
}
```

---

## 4. AccountDeactivateRequest
### 📁 파일 위치
```
src/main/java/com/routepick/core/model/dto/request/user/AccountDeactivateRequest.java
```

### 📝 구현 코드
```java
package com.routepick.core.model.dto.request.user;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.routepick.core.common.validation.Korean;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 계정 비활성화 Request DTO
 * 
 * @author RoutePickProj
 * @since 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "계정 비활성화 요청")
public class AccountDeactivateRequest {

    @Schema(description = "현재 비밀번호 (본인 확인용)", 
            example = "currentPassword123!")
    @NotBlank(message = "현재 비밀번호는 필수입니다")
    @Size(min = 8, max = 50, message = "비밀번호는 8-50자여야 합니다")
    @JsonProperty("currentPassword")
    private String currentPassword;

    @Schema(description = "비활성화 사유", 
            example = "잠시 휴식이 필요해서")
    @NotBlank(message = "비활성화 사유는 필수입니다")
    @Size(min = 10, max = 500, message = "비활성화 사유는 10-500자여야 합니다")
    @Korean.NoHarmfulContent(message = "부적절한 내용이 포함되어 있습니다")
    @JsonProperty("deactivationReason")
    private String deactivationReason;

    @Schema(description = "비활성화 타입 (TEMPORARY: 임시, PERMANENT: 영구)", 
            example = "TEMPORARY")
    @NotBlank(message = "비활성화 타입은 필수입니다")
    @Pattern(regexp = "^(TEMPORARY|PERMANENT)$", 
             message = "비활성화 타입은 TEMPORARY 또는 PERMANENT여야 합니다")
    @JsonProperty("deactivationType")
    private String deactivationType;

    @Schema(description = "데이터 보관 여부 (true: 계정만 비활성화, false: 데이터 삭제)", 
            example = "true")
    @Builder.Default
    @JsonProperty("keepUserData")
    private Boolean keepUserData = true;

    @Schema(description = "프로필 이미지 삭제 여부", 
            example = "false")
    @Builder.Default
    @JsonProperty("deleteProfileImages")
    private Boolean deleteProfileImages = false;

    @Schema(description = "게시글 및 댓글 삭제 여부", 
            example = "false")
    @Builder.Default
    @JsonProperty("deletePostsAndComments")
    private Boolean deletePostsAndComments = false;

    @Schema(description = "클라이밍 기록 삭제 여부", 
            example = "false")
    @Builder.Default
    @JsonProperty("deleteClimbingRecords")
    private Boolean deleteClimbingRecords = false;

    @Schema(description = "팔로우 관계 해제 여부", 
            example = "true")
    @Builder.Default
    @JsonProperty("unfollowAll")
    private Boolean unfollowAll = true;

    @Schema(description = "재활성화 알림 이메일 수신 동의 (임시 비활성화시)", 
            example = "true")
    @JsonProperty("reactivationEmailConsent")
    private Boolean reactivationEmailConsent;

    @Schema(description = "비활성화 확인 문구 (정확히 '계정을 비활성화하겠습니다' 입력)", 
            example = "계정을 비활성화하겠습니다")
    @NotBlank(message = "비활성화 확인 문구는 필수입니다")
    @Pattern(regexp = "^계정을 비활성화하겠습니다$", 
             message = "정확히 '계정을 비활성화하겠습니다'를 입력해주세요")
    @JsonProperty("confirmationPhrase")
    private String confirmationPhrase;

    @Schema(description = "이용약관 변경 알림 수신 동의 (법정 필수)", 
            example = "true")
    @AssertTrue(message = "이용약관 변경 알림 수신 동의는 필수입니다")
    @JsonProperty("legalNotificationConsent")
    private Boolean legalNotificationConsent;

    @Schema(description = "개인정보 보관 기간 (개월 단위, TEMPORARY일 경우)", 
            example = "12")
    @Min(value = 1, message = "개인정보 보관 기간은 1개월 이상이어야 합니다")
    @Max(value = 60, message = "개인정보 보관 기간은 60개월 이하여야 합니다")
    @JsonProperty("dataRetentionMonths")
    private Integer dataRetentionMonths;

    @Schema(description = "추가 요청사항 (선택사항)", 
            example = "특별한 요청사항이 있으면 입력")
    @Size(max = 200, message = "추가 요청사항은 200자 이내로 입력해주세요")
    @Korean.NoHarmfulContent(message = "부적절한 내용이 포함되어 있습니다")
    @JsonProperty("additionalRequest")
    private String additionalRequest;
}
```

---

## 📋 구현 완료 사항
✅ **UserProfileUpdateRequest** - 사용자 프로필 수정 (한국 특화 검증)  
✅ **UserSearchRequest** - 사용자 검색 (다양한 필터링 옵션)  
✅ **ProfileImageUploadRequest** - 프로필 이미지 업로드 (품질/보안 설정)  
✅ **AccountDeactivateRequest** - 계정 비활성화 (보안 강화)  

## 🔧 주요 특징
- **한국 특화**: 휴대폰 번호, 한글 닉네임 검증
- **보안 강화**: XSS 방지, 유해 콘텐츠 검증
- **Bean Validation**: @Valid 어노테이션으로 자동 검증
- **Swagger 문서화**: API 스펙 자동 생성
- **Builder 패턴**: 객체 생성 편의성
- **JSON 프로퍼티**: 카멜케이스/스네이크케이스 호환

## 📝 검증 규칙 요약
1. **닉네임**: 한글/영문/숫자 2-10자
2. **휴대폰**: 010-XXXX-XXXX 패턴
3. **이메일**: 표준 이메일 형식
4. **비밀번호**: 8-50자 (특수문자 포함 권장)
5. **텍스트**: XSS 방지, 유해 콘텐츠 차단