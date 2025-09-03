# Step 7-2d: 사용자 및 프로필 관리 Response DTOs

## 📋 구현 목표
사용자 및 프로필 관리를 위한 4개 Response DTO 클래스 구현:
1. **UserProfileResponse** - 사용자 프로필 정보 응답
2. **UserSearchResponse** - 사용자 검색 결과 응답  
3. **FollowStatsResponse** - 팔로우 통계 정보 응답
4. **UserSummaryResponse** - 사용자 요약 정보 응답

## 🎯 핵심 구현 사항
- **데이터 보안**: 민감정보 마스킹 처리
- **성능 최적화**: 필요한 데이터만 포함
- **API 문서화**: Swagger 어노테이션
- **JSON 직렬화**: 최적화된 응답 구조

---

## 1. UserProfileResponse
### 📁 파일 위치
```
src/main/java/com/routepick/core/model/dto/response/user/UserProfileResponse.java
```

### 📝 구현 코드
```java
package com.routepick.core.model.dto.response.user;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 사용자 프로필 정보 Response DTO
 * 
 * @author RoutePickProj
 * @since 1.0
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

    @Schema(description = "이메일 (마스킹 처리)", example = "user***@example.com")
    @JsonProperty("email")
    private String email;

    @Schema(description = "닉네임", example = "클라이머김")
    @JsonProperty("nickName")
    private String nickName;

    @Schema(description = "실명 (본인만 조회 가능)", example = "김철수")
    @JsonProperty("realName")
    private String realName;

    @Schema(description = "휴대폰 번호 (마스킹 처리)", example = "010-****-5678")
    @JsonProperty("phoneNumber")
    private String phoneNumber;

    @Schema(description = "생년월일", example = "1995-03-15")
    @JsonFormat(pattern = "yyyy-MM-dd")
    @JsonProperty("birthDate")
    private LocalDate birthDate;

    @Schema(description = "성별", example = "M")
    @JsonProperty("gender")
    private String gender;

    @Schema(description = "자기소개", example = "클라이밍을 사랑하는 초보자입니다!")
    @JsonProperty("bio")
    private String bio;

    @Schema(description = "관심 지역", example = "서울특별시 강남구")
    @JsonProperty("interestedRegion")
    private String interestedRegion;

    @Schema(description = "프로필 이미지 URL", example = "https://cdn.routepick.com/profiles/123/profile.jpg")
    @JsonProperty("profileImageUrl")
    private String profileImageUrl;

    @Schema(description = "커버 이미지 URL", example = "https://cdn.routepick.com/profiles/123/cover.jpg")
    @JsonProperty("coverImageUrl")
    private String coverImageUrl;

    @Schema(description = "프로필 공개 설정", example = "PUBLIC")
    @JsonProperty("profileVisibility")
    private String profileVisibility;

    @Schema(description = "계정 상태", example = "ACTIVE")
    @JsonProperty("accountStatus")
    private String accountStatus;

    @Schema(description = "이메일 인증 상태", example = "true")
    @JsonProperty("emailVerified")
    private Boolean emailVerified;

    @Schema(description = "휴대폰 인증 상태", example = "true")
    @JsonProperty("phoneVerified")
    private Boolean phoneVerified;

    @Schema(description = "클라이밍 경력 (개월)", example = "24")
    @JsonProperty("climbingExperienceMonths")
    private Integer climbingExperienceMonths;

    @Schema(description = "선호 클라이밍 스타일", example = "볼더링")
    @JsonProperty("preferredClimbingStyle")
    private String preferredClimbingStyle;

    @Schema(description = "인스타그램 계정", example = "@climber_kim")
    @JsonProperty("instagramAccount")
    private String instagramAccount;

    @Schema(description = "위치 정보 공유 동의", example = "true")
    @JsonProperty("locationSharingConsent")
    private Boolean locationSharingConsent;

    @Schema(description = "가입일")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonProperty("createdAt")
    private LocalDateTime createdAt;

    @Schema(description = "마지막 접속일")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonProperty("lastLoginAt")
    private LocalDateTime lastLoginAt;

    @Schema(description = "팔로우 통계")
    @JsonProperty("followStats")
    private FollowStatsInfo followStats;

    @Schema(description = "클라이밍 통계")
    @JsonProperty("climbingStats")
    private ClimbingStatsInfo climbingStats;

    @Schema(description = "선호 태그 목록")
    @JsonProperty("preferredTags")
    private List<TagInfo> preferredTags;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "팔로우 통계 정보")
    public static class FollowStatsInfo {
        @Schema(description = "팔로워 수", example = "150")
        @JsonProperty("followersCount")
        private Long followersCount;

        @Schema(description = "팔로잉 수", example = "80")
        @JsonProperty("followingCount")
        private Long followingCount;

        @Schema(description = "상호 팔로우 수", example = "25")
        @JsonProperty("mutualFollowsCount")
        private Long mutualFollowsCount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "클라이밍 통계 정보")
    public static class ClimbingStatsInfo {
        @Schema(description = "총 클라이밍 횟수", example = "245")
        @JsonProperty("totalClimbs")
        private Long totalClimbs;

        @Schema(description = "최고 난이도 (V 등급)", example = "V7")
        @JsonProperty("maxDifficultyV")
        private String maxDifficultyV;

        @Schema(description = "최고 난이도 (YDS 등급)", example = "5.12a")
        @JsonProperty("maxDifficultyYds")
        private String maxDifficultyYds;

        @Schema(description = "선호 체육관 수", example = "5")
        @JsonProperty("preferredGymsCount")
        private Integer preferredGymsCount;

        @Schema(description = "이번 달 클라이밍 횟수", example = "12")
        @JsonProperty("currentMonthClimbs")
        private Integer currentMonthClimbs;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "태그 정보")
    public static class TagInfo {
        @Schema(description = "태그 ID", example = "15")
        @JsonProperty("tagId")
        private Long tagId;

        @Schema(description = "태그 이름", example = "오버행")
        @JsonProperty("tagName")
        private String tagName;

        @Schema(description = "태그 타입", example = "WALL_ANGLE")
        @JsonProperty("tagType")
        private String tagType;

        @Schema(description = "선호도 점수", example = "8.5")
        @JsonProperty("preferenceScore")
        private Double preferenceScore;
    }
}
```

---

## 2. UserSearchResponse
### 📁 파일 위치
```
src/main/java/com/routepick/core/model/dto/response/user/UserSearchResponse.java
```

### 📝 구현 코드
```java
package com.routepick.core.model.dto.response.user;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 사용자 검색 결과 Response DTO
 * 
 * @author RoutePickProj
 * @since 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "사용자 검색 결과 응답")
public class UserSearchResponse {

    @Schema(description = "검색 키워드", example = "클라이머")
    @JsonProperty("keyword")
    private String keyword;

    @Schema(description = "검색 타입", example = "NICKNAME")
    @JsonProperty("searchType")
    private String searchType;

    @Schema(description = "총 검색 결과 수", example = "127")
    @JsonProperty("totalElements")
    private Long totalElements;

    @Schema(description = "총 페이지 수", example = "7")
    @JsonProperty("totalPages")
    private Integer totalPages;

    @Schema(description = "현재 페이지 번호", example = "0")
    @JsonProperty("currentPage")
    private Integer currentPage;

    @Schema(description = "페이지 크기", example = "20")
    @JsonProperty("pageSize")
    private Integer pageSize;

    @Schema(description = "다음 페이지 존재 여부", example = "true")
    @JsonProperty("hasNext")
    private Boolean hasNext;

    @Schema(description = "이전 페이지 존재 여부", example = "false")
    @JsonProperty("hasPrevious")
    private Boolean hasPrevious;

    @Schema(description = "검색 결과 목록")
    @JsonProperty("users")
    private List<UserSummary> users;

    @Schema(description = "추천 사용자 목록 (검색 결과가 적을 때)")
    @JsonProperty("recommendedUsers")
    private List<UserSummary> recommendedUsers;

    @Schema(description = "검색 필터 정보")
    @JsonProperty("appliedFilters")
    private SearchFilters appliedFilters;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "사용자 요약 정보")
    public static class UserSummary {
        @Schema(description = "사용자 ID", example = "123")
        @JsonProperty("userId")
        private Long userId;

        @Schema(description = "닉네임", example = "클라이머김")
        @JsonProperty("nickName")
        private String nickName;

        @Schema(description = "실명 (검색 타입이 NAME일 경우만)", example = "김철수")
        @JsonProperty("realName")
        private String realName;

        @Schema(description = "프로필 이미지 URL", example = "https://cdn.routepick.com/profiles/123/profile.jpg")
        @JsonProperty("profileImageUrl")
        private String profileImageUrl;

        @Schema(description = "자기소개", example = "클라이밍을 사랑하는 초보자입니다!")
        @JsonProperty("bio")
        private String bio;

        @Schema(description = "관심 지역", example = "서울특별시 강남구")
        @JsonProperty("interestedRegion")
        private String interestedRegion;

        @Schema(description = "클라이밍 경력 (개월)", example = "24")
        @JsonProperty("climbingExperienceMonths")
        private Integer climbingExperienceMonths;

        @Schema(description = "선호 클라이밍 스타일", example = "볼더링")
        @JsonProperty("preferredClimbingStyle")
        private String preferredClimbingStyle;

        @Schema(description = "팔로워 수", example = "150")
        @JsonProperty("followersCount")
        private Long followersCount;

        @Schema(description = "팔로잉 수", example = "80")
        @JsonProperty("followingCount")
        private Long followingCount;

        @Schema(description = "총 클라이밍 횟수", example = "245")
        @JsonProperty("totalClimbs")
        private Long totalClimbs;

        @Schema(description = "최고 난이도 (V 등급)", example = "V7")
        @JsonProperty("maxDifficultyV")
        private String maxDifficultyV;

        @Schema(description = "팔로우 관계", example = "NOT_FOLLOWING")
        @JsonProperty("followStatus")
        private String followStatus; // NOT_FOLLOWING, FOLLOWING, MUTUAL

        @Schema(description = "온라인 상태", example = "true")
        @JsonProperty("isOnline")
        private Boolean isOnline;

        @Schema(description = "프로필 공개 설정", example = "PUBLIC")
        @JsonProperty("profileVisibility")
        private String profileVisibility;

        @Schema(description = "검색 관련도 점수 (0.0 ~ 1.0)", example = "0.85")
        @JsonProperty("relevanceScore")
        private Double relevanceScore;

        @Schema(description = "상호 친구 수", example = "5")
        @JsonProperty("mutualFriendsCount")
        private Integer mutualFriendsCount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "검색 필터 정보")
    public static class SearchFilters {
        @Schema(description = "팔로워만 검색 여부", example = "false")
        @JsonProperty("followersOnly")
        private Boolean followersOnly;

        @Schema(description = "활성 사용자만 검색 여부", example = "true")
        @JsonProperty("activeUsersOnly")
        private Boolean activeUsersOnly;

        @Schema(description = "프로필 이미지가 있는 사용자만", example = "false")
        @JsonProperty("withProfileImageOnly")
        private Boolean withProfileImageOnly;

        @Schema(description = "클라이밍 경력 최소 개월", example = "6")
        @JsonProperty("minExperienceMonths")
        private Integer minExperienceMonths;

        @Schema(description = "클라이밍 경력 최대 개월", example = "120")
        @JsonProperty("maxExperienceMonths")
        private Integer maxExperienceMonths;

        @Schema(description = "지역 필터", example = "서울특별시")
        @JsonProperty("regionFilter")
        private String regionFilter;

        @Schema(description = "정렬 기준", example = "RELEVANCE")
        @JsonProperty("sortBy")
        private String sortBy;

        @Schema(description = "정렬 방향", example = "DESC")
        @JsonProperty("sortDirection")
        private String sortDirection;
    }
}
```

---

## 3. FollowStatsResponse
### 📁 파일 위치
```
src/main/java/com/routepick/core/model/dto/response/user/FollowStatsResponse.java
```

### 📝 구현 코드
```java
package com.routepick.core.model.dto.response.user;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 팔로우 통계 정보 Response DTO
 * 
 * @author RoutePickProj
 * @since 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "팔로우 통계 정보 응답")
public class FollowStatsResponse {

    @Schema(description = "사용자 ID", example = "123")
    @JsonProperty("userId")
    private Long userId;

    @Schema(description = "닉네임", example = "클라이머김")
    @JsonProperty("nickName")
    private String nickName;

    @Schema(description = "기본 통계")
    @JsonProperty("basicStats")
    private BasicFollowStats basicStats;

    @Schema(description = "성장 통계")
    @JsonProperty("growthStats")
    private GrowthStats growthStats;

    @Schema(description = "상호작용 통계")
    @JsonProperty("interactionStats")
    private InteractionStats interactionStats;

    @Schema(description = "최근 팔로워 목록 (최대 10명)")
    @JsonProperty("recentFollowers")
    private List<FollowerInfo> recentFollowers;

    @Schema(description = "추천 팔로우 목록 (최대 5명)")
    @JsonProperty("recommendedFollows")
    private List<RecommendedUser> recommendedFollows;

    @Schema(description = "통계 업데이트 시간")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonProperty("lastUpdatedAt")
    private LocalDateTime lastUpdatedAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "기본 팔로우 통계")
    public static class BasicFollowStats {
        @Schema(description = "팔로워 수", example = "150")
        @JsonProperty("followersCount")
        private Long followersCount;

        @Schema(description = "팔로잉 수", example = "80")
        @JsonProperty("followingCount")
        private Long followingCount;

        @Schema(description = "상호 팔로우 수", example = "25")
        @JsonProperty("mutualFollowsCount")
        private Long mutualFollowsCount;

        @Schema(description = "팔로우 비율 (팔로잉/팔로워)", example = "0.53")
        @JsonProperty("followRatio")
        private Double followRatio;

        @Schema(description = "상호 팔로우 비율", example = "0.31")
        @JsonProperty("mutualFollowRatio")
        private Double mutualFollowRatio;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "성장 통계")
    public static class GrowthStats {
        @Schema(description = "일간 팔로워 증가", example = "5")
        @JsonProperty("dailyFollowerGrowth")
        private Integer dailyFollowerGrowth;

        @Schema(description = "주간 팔로워 증가", example = "23")
        @JsonProperty("weeklyFollowerGrowth")
        private Integer weeklyFollowerGrowth;

        @Schema(description = "월간 팔로워 증가", example = "87")
        @JsonProperty("monthlyFollowerGrowth")
        private Integer monthlyFollowerGrowth;

        @Schema(description = "최고 일일 증가량", example = "15")
        @JsonProperty("maxDailyGrowth")
        private Integer maxDailyGrowth;

        @Schema(description = "평균 일일 증가량", example = "2.3")
        @JsonProperty("averageDailyGrowth")
        private Double averageDailyGrowth;

        @Schema(description = "성장률 (%)", example = "12.5")
        @JsonProperty("growthRate")
        private Double growthRate;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "상호작용 통계")
    public static class InteractionStats {
        @Schema(description = "내가 언팔한 사용자 수", example = "5")
        @JsonProperty("unfollowedByMeCount")
        private Integer unfollowedByMeCount;

        @Schema(description = "나를 언팔한 사용자 수", example = "3")
        @JsonProperty("unfollowedMeCount")
        private Integer unfollowedMeCount;

        @Schema(description = "차단한 사용자 수", example = "2")
        @JsonProperty("blockedUsersCount")
        private Integer blockedUsersCount;

        @Schema(description = "나를 차단한 사용자 수 (추정)", example = "1")
        @JsonProperty("blockedByUsersCount")
        private Integer blockedByUsersCount;

        @Schema(description = "팔로워와의 평균 상호작용 점수", example = "7.2")
        @JsonProperty("averageInteractionScore")
        private Double averageInteractionScore;

        @Schema(description = "활성 팔로워 비율 (%)", example = "68.5")
        @JsonProperty("activeFollowersRate")
        private Double activeFollowersRate;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "팔로워 정보")
    public static class FollowerInfo {
        @Schema(description = "사용자 ID", example = "456")
        @JsonProperty("userId")
        private Long userId;

        @Schema(description = "닉네임", example = "볼더러박")
        @JsonProperty("nickName")
        private String nickName;

        @Schema(description = "프로필 이미지 URL", example = "https://cdn.routepick.com/profiles/456/profile.jpg")
        @JsonProperty("profileImageUrl")
        private String profileImageUrl;

        @Schema(description = "팔로우 시작일")
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        @JsonProperty("followedAt")
        private LocalDateTime followedAt;

        @Schema(description = "상호 팔로우 여부", example = "true")
        @JsonProperty("isMutual")
        private Boolean isMutual;

        @Schema(description = "팔로워의 팔로워 수", example = "89")
        @JsonProperty("followerCount")
        private Long followerCount;

        @Schema(description = "공통 팔로워 수", example = "12")
        @JsonProperty("commonFollowersCount")
        private Integer commonFollowersCount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "추천 사용자 정보")
    public static class RecommendedUser {
        @Schema(description = "사용자 ID", example = "789")
        @JsonProperty("userId")
        private Long userId;

        @Schema(description = "닉네임", example = "루트세터이")
        @JsonProperty("nickName")
        private String nickName;

        @Schema(description = "프로필 이미지 URL", example = "https://cdn.routepick.com/profiles/789/profile.jpg")
        @JsonProperty("profileImageUrl")
        private String profileImageUrl;

        @Schema(description = "추천 이유", example = "공통 관심사")
        @JsonProperty("recommendationReason")
        private String recommendationReason;

        @Schema(description = "추천 점수", example = "8.7")
        @JsonProperty("recommendationScore")
        private Double recommendationScore;

        @Schema(description = "공통 팔로워 수", example = "8")
        @JsonProperty("commonFollowersCount")
        private Integer commonFollowersCount;

        @Schema(description = "공통 관심 태그 수", example = "5")
        @JsonProperty("commonTagsCount")
        private Integer commonTagsCount;

        @Schema(description = "팔로워 수", example = "234")
        @JsonProperty("followerCount")
        private Long followerCount;
    }
}
```

---

## 4. UserSummaryResponse
### 📁 파일 위치
```
src/main/java/com/routepick/core/model/dto/response/user/UserSummaryResponse.java
```

### 📝 구현 코드
```java
package com.routepick.core.model.dto.response.user;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 사용자 요약 정보 Response DTO
 * 
 * @author RoutePickProj
 * @since 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "사용자 요약 정보 응답")
public class UserSummaryResponse {

    @Schema(description = "사용자 ID", example = "123")
    @JsonProperty("userId")
    private Long userId;

    @Schema(description = "닉네임", example = "클라이머김")
    @JsonProperty("nickName")
    private String nickName;

    @Schema(description = "프로필 이미지 URL", example = "https://cdn.routepick.com/profiles/123/profile.jpg")
    @JsonProperty("profileImageUrl")
    private String profileImageUrl;

    @Schema(description = "자기소개", example = "클라이밍을 사랑하는 초보자입니다!")
    @JsonProperty("bio")
    private String bio;

    @Schema(description = "관심 지역", example = "서울특별시 강남구")
    @JsonProperty("interestedRegion")
    private String interestedRegion;

    @Schema(description = "계정 상태", example = "ACTIVE")
    @JsonProperty("accountStatus")
    private String accountStatus;

    @Schema(description = "온라인 상태", example = "true")
    @JsonProperty("isOnline")
    private Boolean isOnline;

    @Schema(description = "마지막 접속일")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonProperty("lastLoginAt")
    private LocalDateTime lastLoginAt;

    @Schema(description = "기본 통계")
    @JsonProperty("basicStats")
    private BasicUserStats basicStats;

    @Schema(description = "클라이밍 요약")
    @JsonProperty("climbingSummary")
    private ClimbingSummary climbingSummary;

    @Schema(description = "소셜 요약")
    @JsonProperty("socialSummary")
    private SocialSummary socialSummary;

    @Schema(description = "선호 태그 (최대 5개)")
    @JsonProperty("topPreferredTags")
    private List<String> topPreferredTags;

    @Schema(description = "최근 활동 요약")
    @JsonProperty("recentActivity")
    private RecentActivitySummary recentActivity;

    @Schema(description = "배지 정보")
    @JsonProperty("badges")
    private List<BadgeInfo> badges;

    @Schema(description = "프로필 완성도 (%)", example = "85")
    @JsonProperty("profileCompleteness")
    private Integer profileCompleteness;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "기본 사용자 통계")
    public static class BasicUserStats {
        @Schema(description = "팔로워 수", example = "150")
        @JsonProperty("followersCount")
        private Long followersCount;

        @Schema(description = "팔로잉 수", example = "80")
        @JsonProperty("followingCount")
        private Long followingCount;

        @Schema(description = "총 게시글 수", example = "45")
        @JsonProperty("totalPosts")
        private Long totalPosts;

        @Schema(description = "받은 좋아요 수", example = "320")
        @JsonProperty("totalLikesReceived")
        private Long totalLikesReceived;

        @Schema(description = "가입 경과일", example = "245")
        @JsonProperty("daysSinceJoined")
        private Integer daysSinceJoined;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "클라이밍 요약")
    public static class ClimbingSummary {
        @Schema(description = "클라이밍 경력 (개월)", example = "24")
        @JsonProperty("experienceMonths")
        private Integer experienceMonths;

        @Schema(description = "선호 클라이밍 스타일", example = "볼더링")
        @JsonProperty("preferredStyle")
        private String preferredStyle;

        @Schema(description = "총 클라이밍 횟수", example = "245")
        @JsonProperty("totalClimbs")
        private Long totalClimbs;

        @Schema(description = "최고 난이도 (V 등급)", example = "V7")
        @JsonProperty("maxDifficultyV")
        private String maxDifficultyV;

        @Schema(description = "이번 달 클라이밍 횟수", example = "12")
        @JsonProperty("currentMonthClimbs")
        private Integer currentMonthClimbs;

        @Schema(description = "선호 체육관 수", example = "3")
        @JsonProperty("preferredGymsCount")
        private Integer preferredGymsCount;

        @Schema(description = "클라이밍 레벨", example = "INTERMEDIATE")
        @JsonProperty("climbingLevel")
        private String climbingLevel;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "소셜 요약")
    public static class SocialSummary {
        @Schema(description = "상호 팔로우 수", example = "25")
        @JsonProperty("mutualFollowsCount")
        private Long mutualFollowsCount;

        @Schema(description = "평균 게시글 좋아요", example = "7.2")
        @JsonProperty("averagePostLikes")
        private Double averagePostLikes;

        @Schema(description = "댓글 활동 점수", example = "8.5")
        @JsonProperty("commentActivityScore")
        private Double commentActivityScore;

        @Schema(description = "소셜 활동도", example = "HIGH")
        @JsonProperty("socialActivityLevel")
        private String socialActivityLevel;

        @Schema(description = "인기도 점수", example = "72.3")
        @JsonProperty("popularityScore")
        private Double popularityScore;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "최근 활동 요약")
    public static class RecentActivitySummary {
        @Schema(description = "최근 게시글 수 (7일)", example = "3")
        @JsonProperty("recentPostsCount")
        private Integer recentPostsCount;

        @Schema(description = "최근 댓글 수 (7일)", example = "12")
        @JsonProperty("recentCommentsCount")
        private Integer recentCommentsCount;

        @Schema(description = "최근 클라이밍 수 (7일)", example = "5")
        @JsonProperty("recentClimbsCount")
        private Integer recentClimbsCount;

        @Schema(description = "최근 팔로우 수 (7일)", example = "2")
        @JsonProperty("recentFollowsCount")
        private Integer recentFollowsCount;

        @Schema(description = "활동 점수 (0-100)", example = "78")
        @JsonProperty("activityScore")
        private Integer activityScore;

        @Schema(description = "마지막 활동")
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        @JsonProperty("lastActivityAt")
        private LocalDateTime lastActivityAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "배지 정보")
    public static class BadgeInfo {
        @Schema(description = "배지 ID", example = "10")
        @JsonProperty("badgeId")
        private Long badgeId;

        @Schema(description = "배지 이름", example = "볼더링 마스터")
        @JsonProperty("badgeName")
        private String badgeName;

        @Schema(description = "배지 설명", example = "100회 이상 볼더링 완등")
        @JsonProperty("badgeDescription")
        private String badgeDescription;

        @Schema(description = "배지 이미지 URL", example = "https://cdn.routepick.com/badges/bouldering_master.png")
        @JsonProperty("badgeImageUrl")
        private String badgeImageUrl;

        @Schema(description = "배지 등급", example = "GOLD")
        @JsonProperty("badgeLevel")
        private String badgeLevel;

        @Schema(description = "획득일")
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        @JsonProperty("earnedAt")
        private LocalDateTime earnedAt;

        @Schema(description = "희귀도", example = "RARE")
        @JsonProperty("rarity")
        private String rarity;
    }
}
```

---

## 📋 구현 완료 사항
✅ **UserProfileResponse** - 사용자 프로필 정보 (민감정보 마스킹)  
✅ **UserSearchResponse** - 사용자 검색 결과 (페이징, 필터링)  
✅ **FollowStatsResponse** - 팔로우 통계 정보 (성장/상호작용 통계)  
✅ **UserSummaryResponse** - 사용자 요약 정보 (배지, 활동 요약)  

## 🔧 주요 특징
- **데이터 보안**: 민감정보 마스킹 (이메일, 휴대폰)
- **성능 최적화**: @JsonInclude(NON_NULL)로 불필요한 필드 제외
- **풍부한 정보**: 통계, 배지, 추천 정보 포함
- **Swagger 문서화**: 모든 필드에 대한 상세 설명
- **중첩 구조**: Inner class로 관련 정보 그룹화
- **시간 포맷팅**: LocalDateTime 일관성 있는 형식

## 📝 응답 구조 요약
1. **UserProfileResponse**: 개인 프로필 상세 정보
2. **UserSearchResponse**: 검색 결과 + 페이징 + 추천
3. **FollowStatsResponse**: 팔로우 통계 + 성장 분석
4. **UserSummaryResponse**: 요약 정보 + 배지 + 활동

## 🔒 보안 고려사항
- 이메일/휴대폰 마스킹 처리
- 프로필 공개 설정에 따른 정보 필터링
- 민감한 개인정보는 본인만 조회 가능
- @JsonInclude로 null 값 노출 방지