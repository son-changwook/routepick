package com.routepick.common.notification.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum NotificationType {
    SYSTEM("시스템 알림"),
    COMMENT("댓글 알림"),
    LIKE("좋아요 알림"),
    FOLLOW("팔로우 알림"),
    CLIMB("등반 알림"),
    ROUTE_UPDATE("루트 업데이트"),
    PAYMENT("결제 알림");

    private final String description;
}
