package com.routepick.common.domain.user;

import java.time.LocalDate;

import com.routepick.common.domain.common.BaseDomain;
import com.routepick.common.enums.Gender;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import lombok.NoArgsConstructor;

@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
public class UserDetails extends BaseDomain {
    private Long detailId;
    private Long userId;
    private LocalDate birthDate;
    private Gender gender;
    private String address;
    private String detailAddress;
    private String emergencyContact;
    private int height;
    private int weight;
    private int wingspan;
    private int pullReach;
    private int levelId;
    private int branchId;
    private int followingCount;
    private int followerCount;
    private String statusMessage;
    private String bio;
    private String preferences;
    
    
    
}
