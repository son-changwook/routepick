package com.routepick.common.notification.domain;

import java.sql.Timestamp;

import com.routepick.common.notification.enums.NotificationType;

import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class Notification {
    
    private Integer notificationId;
    private Integer userId;
    private NotificationType type;   // enum 사용
    private String title;
    private String content;
    private Boolean isRead;
    private Integer referenceId;
    private String referenceType;
    private Timestamp createdAt;

    @Builder
    public Notification(Integer userId, NotificationType type, String title, String content, 
                       Integer referenceId, String referenceType) {
        this.userId = userId;
        this.type = type;
        this.title = title;
        this.content = content;
        this.isRead = false;
        this.referenceId = referenceId;
        this.referenceType = referenceType;
        this.createdAt = new Timestamp(System.currentTimeMillis());
    }

    public void markAsRead() {
        this.isRead = true;
    }
}
