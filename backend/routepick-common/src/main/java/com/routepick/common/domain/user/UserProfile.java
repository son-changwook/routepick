package com.routepick.common.domain.user;

import com.routepick.common.domain.common.BaseDomain;
import com.routepick.common.enums.Gender;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * 사용자 프로필 정보
 * 사용자의 클라이밍 관련 상세 정보를 저장
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserProfile extends BaseDomain {
    
    private Long detailId;
    private Long userId;
    private Gender gender;
    private int height;
    private int weight;
    private int wingspan;
    private int pullReach;
    private Integer levelId;  // null 허용을 위해 Integer로 변경
    private Integer branchId;  // null 허용을 위해 Integer로 변경
    private int followingCount;
    private int followerCount;
    private String statusMessage;
    private String bio;
    private String preferences;
}
