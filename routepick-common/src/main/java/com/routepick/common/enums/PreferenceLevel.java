package com.routepick.common.enums;

/**
 * 사용자 선호도 레벨
 * 추천 알고리즘의 가중치 계산에 사용
 */
public enum PreferenceLevel {
    LOW("낮음", 0.3),
    MEDIUM("보통", 0.7),
    HIGH("높음", 1.0);

    private final String displayName;
    private final double weight;

    PreferenceLevel(String displayName, double weight) {
        this.displayName = displayName;
        this.weight = weight;
    }

    public String getDisplayName() {
        return displayName;
    }

    public double getWeight() {
        return weight;
    }
}