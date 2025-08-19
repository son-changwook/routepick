package com.routepick.common.enums;

/**
 * 태그 유형 (8가지 카테고리)
 * 추천 시스템의 핵심 분류 체계
 */
public enum TagType {
    STYLE("스타일", "클라이밍 스타일 관련 태그"),
    FEATURE("특징", "루트의 물리적 특징"),
    TECHNIQUE("기술", "필요한 클라이밍 기술"),
    DIFFICULTY("난이도", "체감 난이도 관련"),
    MOVEMENT("동작", "특정 동작이나 무브"),
    HOLD_TYPE("홀드 타입", "홀드의 종류나 형태"),
    WALL_ANGLE("벽면 각도", "벽의 기울기나 각도"),
    OTHER("기타", "기타 분류되지 않는 태그");

    private final String displayName;
    private final String description;

    TagType(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }
}