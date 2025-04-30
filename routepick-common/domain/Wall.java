package com.scw.routepick.domain;

import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class Wall {

    public enum WallStatus {
        ACTIVE, // 활성화
        INACTIVE, // 비활성화
        MAINTENANCE // 유지보수 중
    }

    private Long wallId; // 벽 ID
    private Long branchId; // 지점 ID
    private String wallName; // 벽 이름
    private String description; // 벽 설명
    private LocalDate setDate; // 세팅 날짜
    private LocalDate lastAvailableDate; // 이용 가능 마지막 날짜
    private Boolean removalAfterHours; // 영업 시간 이후 철거 여부
    private WallStatus wallStatus; // 벽 상태
    private LocalDateTime createdAt; // 생성 일시
    private LocalDateTime updatedAt; // 수정 일시
}