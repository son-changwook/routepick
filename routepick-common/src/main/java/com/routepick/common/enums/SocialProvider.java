package com.routepick.common.enums;

/**
 * 소셜 로그인 제공자 (4개)
 * APPLE 제외된 한국 특화 소셜 로그인
 */
public enum SocialProvider {
    GOOGLE("구글", "google"),
    KAKAO("카카오", "kakao"),
    NAVER("네이버", "naver"),
    FACEBOOK("페이스북", "facebook");

    private final String displayName;
    private final String providerId;

    SocialProvider(String displayName, String providerId) {
        this.displayName = displayName;
        this.providerId = providerId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getProviderId() {
        return providerId;
    }
}