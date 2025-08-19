package com.routepick.common;

public final class Constants {

    private Constants() {}

    // JWT Constants
    public static final String JWT_HEADER = "Authorization";
    public static final String JWT_PREFIX = "Bearer ";
    public static final String JWT_CLAIMS_USER_ID = "userId";
    public static final String JWT_CLAIMS_USER_TYPE = "userType";
    public static final String JWT_CLAIMS_EMAIL = "email";

    // Cache Keys
    public static final String CACHE_USER_RECOMMENDATIONS = "user:recommendations:";
    public static final String CACHE_ROUTE_TAGS = "route:tags:";
    public static final String CACHE_USER_PROFILE = "user:profile:";
    public static final String CACHE_GYM_BRANCHES = "gym:branches:";

    // Rate Limiting
    public static final String RATE_LIMIT_API = "api_rate_limit:";
    public static final String RATE_LIMIT_RECOMMENDATION = "recommendation_rate_limit:";

    // File Upload
    public static final long MAX_FILE_SIZE = 50 * 1024 * 1024; // 50MB
    public static final String[] ALLOWED_IMAGE_TYPES = {"jpg", "jpeg", "png", "gif"};
    public static final String[] ALLOWED_VIDEO_TYPES = {"mp4", "avi", "mov"};

    // Pagination
    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 100;

    // Recommendation
    public static final double TAG_WEIGHT = 0.7;
    public static final double LEVEL_WEIGHT = 0.3;
    public static final int MIN_RECOMMENDATION_SCORE = 20;

    // Korean Coordinates (latitude, longitude)
    public static final double KOREA_MIN_LATITUDE = 33.0;
    public static final double KOREA_MAX_LATITUDE = 38.6;
    public static final double KOREA_MIN_LONGITUDE = 124.0;
    public static final double KOREA_MAX_LONGITUDE = 132.0;

    // Phone Number Pattern
    public static final String PHONE_PATTERN = "^01[0-9]-?[0-9]{4}-?[0-9]{4}$";

    // Error Codes
    public static final String ERROR_USER_NOT_FOUND = "USER_NOT_FOUND";
    public static final String ERROR_ROUTE_NOT_FOUND = "ROUTE_NOT_FOUND";
    public static final String ERROR_GYM_NOT_FOUND = "GYM_NOT_FOUND";
    public static final String ERROR_UNAUTHORIZED = "UNAUTHORIZED";
    public static final String ERROR_FORBIDDEN = "FORBIDDEN";
    public static final String ERROR_VALIDATION_FAILED = "VALIDATION_FAILED";
    public static final String ERROR_RATE_LIMIT_EXCEEDED = "RATE_LIMIT_EXCEEDED";
    public static final String ERROR_FILE_UPLOAD_FAILED = "FILE_UPLOAD_FAILED";

    // Social Login Providers
    public static final String PROVIDER_GOOGLE = "GOOGLE";
    public static final String PROVIDER_KAKAO = "KAKAO";
    public static final String PROVIDER_NAVER = "NAVER";
    public static final String PROVIDER_FACEBOOK = "FACEBOOK";

    // User Types
    public static final String USER_TYPE_REGULAR = "REGULAR";
    public static final String USER_TYPE_GYM_ADMIN = "GYM_ADMIN";
    public static final String USER_TYPE_ADMIN = "ADMIN";

    // Tag Types
    public static final String TAG_TYPE_STYLE = "STYLE";
    public static final String TAG_TYPE_FEATURE = "FEATURE";
    public static final String TAG_TYPE_TECHNIQUE = "TECHNIQUE";
    public static final String TAG_TYPE_DIFFICULTY = "DIFFICULTY";
    public static final String TAG_TYPE_MOVEMENT = "MOVEMENT";
    public static final String TAG_TYPE_HOLD_TYPE = "HOLD_TYPE";
    public static final String TAG_TYPE_WALL_ANGLE = "WALL_ANGLE";
    public static final String TAG_TYPE_OTHER = "OTHER";

    // Route Status
    public static final String ROUTE_STATUS_ACTIVE = "ACTIVE";
    public static final String ROUTE_STATUS_RETIRED = "RETIRED";
    public static final String ROUTE_STATUS_MAINTENANCE = "MAINTENANCE";

    // Wall Status
    public static final String WALL_STATUS_ACTIVE = "ACTIVE";
    public static final String WALL_STATUS_INACTIVE = "INACTIVE";
    public static final String WALL_STATUS_MAINTENANCE = "MAINTENANCE";
}