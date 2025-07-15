package com.routepick.common.notification.dto;

import java.sql.Timestamp;

import com.routepick.common.notification.domain.Notification;
import com.routepick.common.notification.enums.NotificationType;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class NotificationDTO {
    
    private Integer notificationId;
    private NotificationType type;
    private String title;
    private String content;
    private Boolean isRead;
    private Integer referenceId;
    private String referenceType;
    private Timestamp createdAt;
    private String typeDescription;  // 알림 타입에 대한 설명

    public static NotificationDTO from(Notification notification) {
        return NotificationDTO.builder()
                .notificationId(notification.getNotificationId())
                .type(notification.getType())
                .title(notification.getTitle())
                .content(notification.getContent())
                .isRead(notification.getIsRead())
                .referenceId(notification.getReferenceId())
                .referenceType(notification.getReferenceType())
                .createdAt(notification.getCreatedAt())
                .typeDescription(notification.getType().getDescription())
                .build();
    }
}
