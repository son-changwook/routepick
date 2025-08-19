package com.routepick.common.enums;

/**
 * 사용자 숙련도 레벨
 */
public enum SkillLevel {
    BEGINNER("초보자", 1),
    INTERMEDIATE("중급자", 2), 
    ADVANCED("고급자", 3),
    EXPERT("전문가", 4);

    private final String displayName;
    private final int level;

    SkillLevel(String displayName, int level) {
        this.displayName = displayName;
        this.level = level;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getLevel() {
        return level;
    }
}