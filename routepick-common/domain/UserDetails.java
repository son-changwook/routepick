package com.scw.routepick.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Data;
import com.fasterxml.jackson.databind.JsonNode;
import com.scw.routepick.common.enums.Gender;

@Data
public class UserDetails {

    private Long detailId; // 상세 정보 ID
    private Long userId; // 유저 ID
    private LocalDate birthDate; // 생년월일
    private Gender gender; // 성별
    private String address; // 주소
    private String detailAddress; // 상세 주소
    private String emergencyContact; // 비상 연락처
    private Integer height; // 키
    private Integer weight; // 몸무게
    private Integer wingspan; // 양옆으로 벌린 팔의 길이
    private Integer pullReach; // 위로 손을 뻗었을 때 손가락 끝이 목표물에 닿는 거리
    private Long levelId; // 레벨 ID
    private Long branchId; // 지점 ID
    private Integer followingCount; // 팔로잉 수
    private Integer followerCount; // 팔로워 수
    private String statusMessage; // 상태 메시지
    private String bio; // 자기소개
    private JsonNode preferences; // 선호 설정
    private LocalDateTime createdAt; // 생성 일시
    private LocalDateTime updatedAt; // 수정 일시

}
