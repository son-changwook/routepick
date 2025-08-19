package com.routepick.common.constants;

/**
 * 공통 상수 정의
 * Backend, Frontend에서 공통으로 사용하는 상수들
 */
public final class Constants {

    private Constants() {
        // 유틸리티 클래스는 인스턴스 생성 방지
    }

    // JWT 관련 상수
    public static final String JWT_HEADER = "Authorization";
    public static final String JWT_PREFIX = "Bearer ";
    public static final String JWT_CLAIMS_USER_ID = "userId";
    public static final String JWT_CLAIMS_EMAIL = "email";
    public static final String JWT_CLAIMS_USER_TYPE = "userType";
    public static final long JWT_ACCESS_TOKEN_EXPIRATION = 1800000L; // 30분
    public static final long JWT_REFRESH_TOKEN_EXPIRATION = 604800000L; // 7일

    // 추천 알고리즘 상수
    public static final double TAG_WEIGHT = 0.7; // 태그 매칭 70%
    public static final double LEVEL_WEIGHT = 0.3; // 레벨 매칭 30%
    public static final int MIN_RECOMMENDATION_SCORE = 20; // 최소 추천 점수
    public static final int MAX_RECOMMENDATIONS_PER_USER = 100; // 사용자당 최대 추천 수

    // 한국 GPS 좌표 범위
    public static final double KOREA_MIN_LATITUDE = 33.0;
    public static final double KOREA_MAX_LATITUDE = 38.6;
    public static final double KOREA_MIN_LONGITUDE = 124.0;
    public static final double KOREA_MAX_LONGITUDE = 132.0;

    // 소셜 로그인 Provider (4개)
    public static final String PROVIDER_GOOGLE = "GOOGLE";
    public static final String PROVIDER_KAKAO = "KAKAO";
    public static final String PROVIDER_NAVER = "NAVER";
    public static final String PROVIDER_FACEBOOK = "FACEBOOK";

    // Redis 캐시 키 패턴
    public static final String CACHE_USER_RECOMMENDATIONS = "user:recommendations:";
    public static final String CACHE_ROUTE_TAGS = "route:tags:";
    public static final String CACHE_USER_PROFILE = "user:profile:";
    public static final String CACHE_GYM_BRANCHES = "gym:branches:";
    public static final String CACHE_TAG_STATISTICS = "tag:statistics:";

    // Redis 캐시 TTL (초)
    public static final long CACHE_TTL_USER_RECOMMENDATIONS = 86400; // 24시간
    public static final long CACHE_TTL_ROUTE_TAGS = 3600; // 1시간
    public static final long CACHE_TTL_USER_PROFILE = 1800; // 30분
    public static final long CACHE_TTL_GYM_BRANCHES = 21600; // 6시간
    public static final long CACHE_TTL_TAG_STATISTICS = 7200; // 2시간

    // API 응답 상수
    public static final String API_SUCCESS_MESSAGE = "요청이 성공적으로 처리되었습니다.";
    public static final String API_ERROR_MESSAGE = "요청 처리 중 오류가 발생했습니다.";
    public static final String API_VALIDATION_ERROR = "입력 데이터 검증에 실패했습니다.";
    public static final String API_UNAUTHORIZED = "인증이 필요합니다.";
    public static final String API_FORBIDDEN = "접근 권한이 없습니다.";
    public static final String API_NOT_FOUND = "요청한 리소스를 찾을 수 없습니다.";

    // 에러 코드
    public static final String ERROR_CODE_VALIDATION = "VALIDATION_ERROR";
    public static final String ERROR_CODE_UNAUTHORIZED = "UNAUTHORIZED";
    public static final String ERROR_CODE_FORBIDDEN = "FORBIDDEN";
    public static final String ERROR_CODE_NOT_FOUND = "NOT_FOUND";
    public static final String ERROR_CODE_INTERNAL_SERVER = "INTERNAL_SERVER_ERROR";
    public static final String ERROR_CODE_DUPLICATE_EMAIL = "DUPLICATE_EMAIL";
    public static final String ERROR_CODE_INVALID_CREDENTIALS = "INVALID_CREDENTIALS";
    public static final String ERROR_CODE_EXPIRED_TOKEN = "EXPIRED_TOKEN";

    // 페이징 상수
    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 100;
    public static final String DEFAULT_SORT_FIELD = "createdAt";
    public static final String DEFAULT_SORT_DIRECTION = "DESC";

    // 파일 업로드 상수
    public static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB
    public static final String[] ALLOWED_IMAGE_EXTENSIONS = {"jpg", "jpeg", "png", "gif", "webp"};
    public static final String[] ALLOWED_VIDEO_EXTENSIONS = {"mp4", "avi", "mov", "wmv"};

    // Rate Limiting 상수
    public static final int RATE_LIMIT_REQUESTS_PER_MINUTE = 60;
    public static final int RATE_LIMIT_REQUESTS_PER_HOUR = 1000;
    public static final int RATE_LIMIT_LOGIN_ATTEMPTS = 5;

    // 알림 타입
    public static final String NOTIFICATION_TYPE_RECOMMENDATION = "RECOMMENDATION";
    public static final String NOTIFICATION_TYPE_NEW_ROUTE = "NEW_ROUTE";
    public static final String NOTIFICATION_TYPE_SOCIAL = "SOCIAL";
    public static final String NOTIFICATION_TYPE_SYSTEM = "SYSTEM";
    public static final String NOTIFICATION_TYPE_MARKETING = "MARKETING";

    // 한국어 메시지
    public static final String MESSAGE_USER_CREATED = "사용자 등록이 완료되었습니다.";
    public static final String MESSAGE_LOGIN_SUCCESS = "로그인에 성공했습니다.";
    public static final String MESSAGE_LOGOUT_SUCCESS = "로그아웃이 완료되었습니다.";
    public static final String MESSAGE_PASSWORD_CHANGED = "비밀번호가 변경되었습니다.";
    public static final String MESSAGE_PROFILE_UPDATED = "프로필이 업데이트되었습니다.";
    public static final String MESSAGE_ROUTE_CREATED = "루트가 등록되었습니다.";
    public static final String MESSAGE_ROUTE_UPDATED = "루트 정보가 업데이트되었습니다.";
    public static final String MESSAGE_ROUTE_DELETED = "루트가 삭제되었습니다.";
    public static final String MESSAGE_RECOMMENDATION_UPDATED = "추천 정보가 업데이트되었습니다.";
}