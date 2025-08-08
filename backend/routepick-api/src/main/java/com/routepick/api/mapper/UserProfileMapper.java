package com.routepick.api.mapper;

import com.routepick.common.domain.user.UserProfile;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 사용자 프로필 정보 매퍼
 */
@Mapper
public interface UserProfileMapper {
    
    /**
     * 사용자 프로필 정보 저장
     */
    int insertUserProfile(UserProfile userProfile);
    
    /**
     * 사용자 프로필 정보 수정
     */
    int updateUserProfile(UserProfile userProfile);
    
    /**
     * 사용자 ID로 프로필 정보 조회
     */
    UserProfile findByUserId(@Param("userId") Long userId);
    
    /**
     * 사용자 프로필 정보 삭제
     */
    int deleteByUserId(@Param("userId") Long userId);
    
    /**
     * 모든 사용자 프로필 정보 조회 (관리자용)
     */
    List<UserProfile> findAll();
} 