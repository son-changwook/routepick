package com.routepick.common.domain.common;  

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.io.Serializable;
import java.time.LocalDateTime;

@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
public abstract class BaseDomain implements Serializable {
    
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Seoul")
    private LocalDateTime createdAt;
    
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Seoul")   
    private LocalDateTime updatedAt;
    
    // created_by와 updated_by 필드는 아래 메서드를 통해
    // 필요한 엔티티 클래스에서만 개별적으로 구현하게 함
    
    /**
     * 데이터 생성 시 감사 정보 설정
     * created_by, updated_by 필드가 있는 엔티티는 이 메서드를 오버라이드하여 구현
     */
    public void setAuditInfoForCreate(String userId) {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }
    
    /**
     * 데이터 수정 시 감사 정보 설정
     * updated_by 필드가 있는 엔티티는 이 메서드를 오버라이드하여 구현
     */
    public void setAuditInfoForUpdate(String userId) {
        this.updatedAt = LocalDateTime.now();
    }
}