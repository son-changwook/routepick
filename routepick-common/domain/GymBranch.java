package com.scw.routepick.domain;

import lombok.Data;
import java.time.LocalDateTime;
import com.fasterxml.jackson.databind.JsonNode;

@Data
public class GymBranch {

    public enum BranchStatus {
        ACTIVE, // 운영중
        INACTIVE, // 휴점
        CLOSED, // 폐점
        PENDING // 승인 대기
    }

    private Long branchId; // 지점 ID
    private Long gymId; // 암장
    private String branchName; // 지점 이름
    private String businessNumber; // 사업자 번호
    private String logoUrl; // 로고 URL
    private String address; // 주소
    private Double latitude; // 위도
    private Double longitude; // 경도
    private String contactPhone; // 연락처
    private JsonNode businessHours; // 영업 시간
    private JsonNode amenities; // 편의시설
    private LocalDateTime createdAt; // 생성 일시
    private LocalDateTime updatedAt; // 수정 일시
    private BranchStatus branchStatus; // 지점 상태

}
