# 📱 Step 4-4b2b2: 앱 팝업 엔티티 설계

> **RoutePickr 앱 팝업 관리** - 실행시 팝업, 트리거 조건, 상세 통계
> 
> **생성일**: 2025-08-20  
> **단계**: 4-4b2b2 (JPA 엔티티 50개 - 앱 팝업 1개)  
> **분할**: step4-4b2b_system_notification_entities.md에서 세분화
> **연관**: step4-4b2b1_notice_banner_entities.md

---

## 📋 파일 개요

이 파일은 **RoutePickr 앱 팝업 관리의 1개 엔티티**를 담고 있습니다.

### 🎯 주요 특징
- **앱 팝업**: 실행 시 팝업, 트리거 조건 설정
- **스타일링**: Modal, Fullscreen, Banner, Alert 지원
- **상세 통계**: 버튼별 클릭, 전환율, 표시시간 분석

### 📊 엔티티 목록 (1개)
1. **AppPopup** - 앱 팝업

---

## 📱 1. AppPopup 엔티티 - 앱 팝업

```java
package com.routepick.domain.notification.entity;

import com.routepick.common.entity.BaseEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 앱 팝업
 * - 앱 실행 시 표시되는 팝업
 * - 공지, 이벤트, 업데이트 안내 등
 * - 표시 조건 및 통계 관리
 */
@Entity
@Table(name = "app_popups", indexes = {
    @Index(name = "idx_popup_active_date", columnList = "is_active, start_date DESC, end_date DESC"),
    @Index(name = "idx_popup_priority", columnList = "priority_level, created_at DESC"),
    @Index(name = "idx_popup_type", columnList = "popup_type"),
    @Index(name = "idx_popup_trigger", columnList = "trigger_type")
})
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class AppPopup extends BaseEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "popup_id")
    private Long popupId;
    
    // ===== 팝업 기본 정보 =====
    
    @NotNull
    @Size(min = 2, max = 200, message = "팝업 제목은 2-200자 사이여야 합니다")
    @Column(name = "popup_title", nullable = false, length = 200)
    private String popupTitle; // 팝업 제목
    
    @NotNull
    @Size(min = 1, max = 1000, message = "팝업 내용은 1-1000자 사이여야 합니다")
    @Column(name = "popup_content", nullable = false, columnDefinition = "TEXT")
    private String popupContent; // 팝업 내용
    
    @Column(name = "popup_type", length = 30)
    private String popupType = "NOTICE"; // NOTICE, EVENT, UPDATE, AD, SURVEY, WELCOME
    
    @Column(name = "popup_style", length = 30)
    private String popupStyle = "MODAL"; // MODAL, FULLSCREEN, BANNER, ALERT
    
    // ===== 이미지 및 디자인 =====
    
    @Column(name = "popup_image_url", length = 500)
    private String popupImageUrl; // 팝업 이미지
    
    @Column(name = "background_image_url", length = 500)
    private String backgroundImageUrl; // 배경 이미지
    
    @Column(name = "icon_url", length = 500)
    private String iconUrl; // 아이콘
    
    @Column(name = "background_color", length = 7)
    private String backgroundColor = "#FFFFFF"; // 배경색
    
    @Column(name = "text_color", length = 7)
    private String textColor = "#000000"; // 텍스트 색상
    
    @Column(name = "button_color", length = 7)
    private String buttonColor = "#007AFF"; // 버튼 색상
    
    // ===== 버튼 설정 =====
    
    @Column(name = "primary_button_text", length = 50)
    private String primaryButtonText = "확인"; // 주 버튼 텍스트
    
    @Column(name = "primary_button_action", length = 200)
    private String primaryButtonAction; // 주 버튼 액션 (URL, 딥링크 등)
    
    @Column(name = "secondary_button_text", length = 50)
    private String secondaryButtonText; // 보조 버튼 텍스트
    
    @Column(name = "secondary_button_action", length = 200)
    private String secondaryButtonAction; // 보조 버튼 액션
    
    @Column(name = "close_button_visible", nullable = false)
    private boolean closeButtonVisible = true; // 닫기 버튼 표시
    
    @Column(name = "auto_close_seconds")
    private Integer autoCloseSeconds; // 자동 닫기 시간 (초)
    
    // ===== 표시 조건 =====
    
    @Column(name = "trigger_type", length = 30)
    private String triggerType = "APP_LAUNCH"; // APP_LAUNCH, LOGIN, FIRST_TIME, INTERVAL
    
    @Column(name = "show_frequency", length = 30)
    private String showFrequency = "ONCE"; // ONCE, DAILY, WEEKLY, ALWAYS
    
    @Column(name = "min_app_version", length = 20)
    private String minAppVersion; // 최소 앱 버전
    
    @Column(name = "max_app_version", length = 20)
    private String maxAppVersion; // 최대 앱 버전
    
    @Column(name = "delay_seconds")
    private Integer delaySeconds = 0; // 표시 지연 시간 (초)
    
    // ===== 우선순위 및 순서 =====
    
    @NotNull
    @Min(value = 1, message = "우선순위는 1 이상이어야 합니다")
    @Max(value = 10, message = "우선순위는 10 이하여야 합니다")
    @Column(name = "priority_level", nullable = false)
    private Integer priorityLevel = 5; // 우선순위 (1: 높음, 10: 낮음)
    
    @Column(name = "display_order")
    private Integer displayOrder = 1; // 같은 우선순위 내 표시 순서
    
    @Column(name = "max_daily_shows")
    private Integer maxDailyShows = 1; // 일일 최대 표시 횟수
    
    @Column(name = "cooldown_hours")
    private Integer cooldownHours = 24; // 재표시 간격 (시간)
    
    // ===== 게시 기간 =====
    
    @Column(name = "start_date")
    private LocalDateTime startDate; // 표시 시작일
    
    @Column(name = "end_date")
    private LocalDateTime endDate; // 표시 종료일
    
    @Column(name = "is_active", nullable = false)
    private boolean isActive = true; // 활성 상태
    
    @Column(name = "is_test_mode", nullable = false)
    private boolean isTestMode = false; // 테스트 모드
    
    // ===== 대상 설정 =====
    
    @Column(name = "target_user_type", length = 20)
    private String targetUserType = "ALL"; // ALL, NEW, RETURNING, VIP
    
    @Column(name = "target_platform", length = 20)
    private String targetPlatform = "ALL"; // ANDROID, IOS, WEB, ALL
    
    @Column(name = "target_region", length = 100)
    private String targetRegion; // 특정 지역
    
    @Column(name = "target_user_count_min")
    private Integer targetUserCountMin; // 최소 사용 횟수
    
    @Column(name = "target_user_count_max")
    private Integer targetUserCountMax; // 최대 사용 횟수
    
    // ===== 통계 정보 =====
    
    @Column(name = "total_shows")
    private Long totalShows = 0L; // 총 표시 횟수
    
    @Column(name = "today_shows")
    private Integer todayShows = 0; // 오늘 표시 횟수
    
    @Column(name = "unique_users_shown")
    private Integer uniqueUsersShown = 0; // 고유 사용자 표시 수
    
    @Column(name = "primary_button_clicks")
    private Integer primaryButtonClicks = 0; // 주 버튼 클릭 수
    
    @Column(name = "secondary_button_clicks")
    private Integer secondaryButtonClicks = 0; // 보조 버튼 클릭 수
    
    @Column(name = "close_button_clicks")
    private Integer closeButtonClicks = 0; // 닫기 버튼 클릭 수
    
    @Column(name = "conversion_count")
    private Integer conversionCount = 0; // 전환 수
    
    @Column(name = "avg_display_duration")
    private Float avgDisplayDuration = 0.0f; // 평균 표시 시간 (초)
    
    // ===== 관리 정보 =====
    
    @Column(name = "created_by")
    private Long createdBy; // 생성자 ID
    
    @Column(name = "approved_by")
    private Long approvedBy; // 승인자 ID
    
    @Column(name = "approved_at")
    private LocalDateTime approvedAt; // 승인일
    
    @Column(name = "admin_memo", columnDefinition = "TEXT")
    private String adminMemo; // 관리자 메모
    
    // ===== 비즈니스 메서드 =====
    
    /**
     * 팝업 타입 한글 표시
     */
    @Transient
    public String getPopupTypeKorean() {
        if (popupType == null) return "공지";
        
        return switch (popupType) {
            case "NOTICE" -> "공지";
            case "EVENT" -> "이벤트";
            case "UPDATE" -> "업데이트";
            case "AD" -> "광고";
            case "SURVEY" -> "설문";
            case "WELCOME" -> "환영";
            default -> popupType;
        };
    }
    
    /**
     * 트리거 타입 한글 표시
     */
    @Transient
    public String getTriggerTypeKorean() {
        if (triggerType == null) return "앱 실행";
        
        return switch (triggerType) {
            case "APP_LAUNCH" -> "앱 실행";
            case "LOGIN" -> "로그인";
            case "FIRST_TIME" -> "첫 실행";
            case "INTERVAL" -> "주기적";
            case "SPECIFIC_PAGE" -> "특정 페이지";
            default -> triggerType;
        };
    }
    
    /**
     * 표시 빈도 한글 표시
     */
    @Transient
    public String getShowFrequencyKorean() {
        if (showFrequency == null) return "한 번만";
        
        return switch (showFrequency) {
            case "ONCE" -> "한 번만";
            case "DAILY" -> "매일";
            case "WEEKLY" -> "매주";
            case "ALWAYS" -> "항상";
            default -> showFrequency;
        };
    }
    
    /**
     * 현재 표시 가능한지 확인
     */
    @Transient
    public boolean isCurrentlyDisplayable() {
        if (!isActive || isTestMode) return false;
        
        LocalDateTime now = LocalDateTime.now();
        
        boolean afterStart = startDate == null || !startDate.isAfter(now);
        boolean beforeEnd = endDate == null || !endDate.isBefore(now);
        
        return afterStart && beforeEnd;
    }
    
    /**
     * 일일 표시 제한 확인
     */
    @Transient
    public boolean canShowToday() {
        return maxDailyShows == null || todayShows < maxDailyShows;
    }
    
    /**
     * 표시 횟수 증가
     */
    public void incrementShowCount() {
        this.totalShows = (totalShows == null ? 0L : totalShows) + 1;
        this.todayShows = (todayShows == null ? 0 : todayShows) + 1;
    }
    
    /**
     * 고유 사용자 수 증가
     */
    public void incrementUniqueUserCount() {
        this.uniqueUsersShown = (uniqueUsersShown == null ? 0 : uniqueUsersShown) + 1;
    }
    
    /**
     * 주 버튼 클릭
     */
    public void clickPrimaryButton() {
        this.primaryButtonClicks = (primaryButtonClicks == null ? 0 : primaryButtonClicks) + 1;
    }
    
    /**
     * 보조 버튼 클릭
     */
    public void clickSecondaryButton() {
        this.secondaryButtonClicks = (secondaryButtonClicks == null ? 0 : secondaryButtonClicks) + 1;
    }
    
    /**
     * 닫기 버튼 클릭
     */
    public void clickCloseButton() {
        this.closeButtonClicks = (closeButtonClicks == null ? 0 : closeButtonClicks) + 1;
    }
    
    /**
     * 전환율 계산
     */
    @Transient
    public float getConversionRate() {
        if (totalShows == null || totalShows == 0) return 0.0f;
        return ((float) (conversionCount == null ? 0 : conversionCount) / totalShows) * 100;
    }
    
    /**
     * 클릭률 계산
     */
    @Transient
    public float getClickThroughRate() {
        if (totalShows == null || totalShows == 0) return 0.0f;
        
        int totalClicks = (primaryButtonClicks == null ? 0 : primaryButtonClicks) +
                         (secondaryButtonClicks == null ? 0 : secondaryButtonClicks);
        
        return ((float) totalClicks / totalShows) * 100;
    }
    
    /**
     * 팝업 승인
     */
    public void approve(Long adminId) {
        this.approvedBy = adminId;
        this.approvedAt = LocalDateTime.now();
        this.isActive = true;
    }
    
    /**
     * 오늘 표시 횟수 리셋
     */
    public void resetTodayShows() {
        this.todayShows = 0;
    }
    
    @Override
    public Long getId() {
        return popupId;
    }
}
```

---

## 🎯 엔티티 설계 특징

### 📱 AppPopup 엔티티 핵심 기능

#### 1. **팝업 스타일링 시스템**
- **스타일 타입**: Modal, Fullscreen, Banner, Alert
- **색상 커스터마이징**: 배경색, 텍스트색, 버튼색 HEX 설정
- **이미지 지원**: 팝업 이미지, 배경 이미지, 아이콘
- **브랜딩 일관성**: 앱 테마와 일치하는 디자인

#### 2. **버튼 액션 시스템**
- **주 버튼**: 주요 액션 (확인, 이동하기, 참여하기)
- **보조 버튼**: 부가 액션 (나중에, 더보기, 취소)
- **닫기 버튼**: 표시/숨김 옵션, 강제 액션 가능
- **자동 닫기**: 지정된 시간 후 자동 닫힘

#### 3. **표시 조건 엔진**
- **트리거 타입**: 앱 실행, 로그인, 첫 실행, 주기적, 특정 페이지
- **표시 빈도**: 한 번만, 매일, 매주, 항상
- **앱 버전**: 최소/최대 버전 범위 설정
- **지연 시간**: 앱 실행 후 N초 지연 표시

#### 4. **우선순위 및 제한**
- **우선순위 레벨**: 1(매우 높음) ~ 10(매우 낮음)
- **표시 순서**: 같은 우선순위 내 세부 순서
- **일일 제한**: 하루 최대 표시 횟수
- **쿨다운**: 재표시 간격 (시간 단위)

#### 5. **고급 타겟팅**
- **사용자 분류**: 전체, 신규, 재방문, VIP
- **플랫폼 필터**: Android, iOS, Web, 전체
- **지역 타겟팅**: 특정 지역 사용자
- **사용 빈도**: 최소/최대 앱 사용 횟수

#### 6. **상세 통계 분석**
- **표시 통계**: 총/일일 표시 횟수, 고유 사용자 수
- **버튼별 클릭**: 주/보조/닫기 버튼 각각 추적
- **성과 지표**: 전환율, 클릭률 자동 계산
- **평균 표시 시간**: 사용자 관심도 측정

#### 7. **테스트 및 관리**
- **테스트 모드**: 실제 서비스 영향 없이 테스트
- **승인 프로세스**: 관리자 검토 후 활성화
- **관리자 메모**: 내부 관리 용도

### 📊 인덱스 최적화
- **활성 팝업**: `(is_active, start_date DESC, end_date DESC)`
- **우선순위**: `(priority_level, created_at DESC)`
- **타입별**: `popup_type`
- **트리거별**: `trigger_type`

### 🔒 보안 고려사항
- 테스트 모드와 실제 모드 완전 분리
- 외부 링크 액션 검증 필요
- XSS 방지를 위한 입력값 검증
- 승인 프로세스를 통한 무단 팝업 방지

---

## 📈 성능 최적화

### 💾 메모리 캐싱
- 활성 팝업 리스트: 앱 시작 시 로드
- 표시 조건 검사: 클라이언트 캐싱
- 통계 데이터: 비동기 업데이트

### 🚀 배치 처리
- 일일 통계 집계: 매일 새벽 배치
- 오늘 표시 횟수 리셋: 자정 배치
- 만료된 팝업 정리: 주간 배치

### 📱 모바일 최적화
- 이미지 최적화: WebP 변환, 압축
- 네트워크 최적화: 필수 데이터만 전송
- 배터리 최적화: 최소한의 백그라운드 작업

---

## 💡 비즈니스 활용 예시

### 🎯 신규 사용자 환영
```java
AppPopup.builder()
    .popupTitle("RoutePickr에 오신 것을 환영합니다!")
    .popupContent("클라이밍의 새로운 경험을 시작해보세요")
    .popupType("WELCOME")
    .triggerType("FIRST_TIME")
    .showFrequency("ONCE")
    .targetUserType("NEW")
    .build();
```

### 📢 이벤트 공지
```java
AppPopup.builder()
    .popupTitle("겨울 시즌 이벤트")
    .popupContent("12월 한 달간 프리미엄 루트 무료 체험!")
    .popupType("EVENT")
    .triggerType("APP_LAUNCH")
    .showFrequency("DAILY")
    .maxDailyShows(1)
    .build();
```

### 🔄 앱 업데이트 안내
```java
AppPopup.builder()
    .popupTitle("새로운 기능이 추가되었어요")
    .popupContent("AR 루트 가이드를 체험해보세요")
    .popupType("UPDATE")
    .triggerType("LOGIN")
    .maxAppVersion("1.2.0")
    .build();
```

---

## ✅ 설계 완료 체크리스트

### 앱 팝업 엔티티 (1개)
- [x] **AppPopup** - 앱 팝업 (트리거 조건, 우선순위, 상세 통계)

### 핵심 기능
- [x] 스타일링 시스템 (Modal, Fullscreen, Banner, Alert)
- [x] 버튼 액션 시스템 (주/보조/닫기 버튼)
- [x] 표시 조건 엔진 (트리거, 빈도, 버전, 지연)
- [x] 우선순위 및 제한 (레벨, 순서, 일일제한, 쿨다운)

### 고급 기능
- [x] 타겟팅 시스템 (사용자 분류, 플랫폼, 지역)
- [x] 상세 통계 분석 (표시, 클릭, 전환, 시간)
- [x] 테스트 모드 (실서비스 영향 없는 테스트)
- [x] 승인 프로세스 (관리자 검토)

### 성능 최적화
- [x] 인덱스 최적화 (활성, 우선순위, 타입별)
- [x] 캐싱 전략 (메모리, 클라이언트)
- [x] 배치 처리 (통계, 정리, 리셋)
- [x] 모바일 최적화 (이미지, 네트워크, 배터리)

---

**📝 완료**: 시스템 알림 엔티티 세분화 작업 완료 (3개 → 2개 파일로 분할)