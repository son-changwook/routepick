package com.routepick.api.mapper;

import com.routepick.common.domain.user.User;
import com.routepick.common.enums.UserType;     
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

@Mapper
public interface UserMapper {
    
    /**
     * 사용자명으로 일반 사용자 조회
     */
    Optional<User> findByUsername(@Param("username") String username);
    
    /**
     * 이메일로 일반 사용자 조회
     */
    Optional<User> findByEmail(@Param("email") String email);
    
    /**
     * 사용자 ID로 일반 사용자 조회
     */
    Optional<User> findById(@Param("userId") Long userId);
    
    /**
     * 활성화된 일반 사용자 목록 조회
     */
    List<User> findAllActiveUsers();
    
    /**
     * 일반 사용자 저장
     */
    int insertUser(User user);
    
    /**
     * 일반 사용자 정보 수정
     */
    int updateUser(User user);
    
    /**
     * 일반 사용자 삭제 (비활성화)
     */
    int deleteUser(@Param("userId") Long userId);
    
    /**
     * 이메일 중복 확인
     */
    boolean existsByEmail(@Param("email") String email);
    
    /**
     * 사용자명 중복 확인
     */
    boolean existsByUsername(@Param("username") String username);
    
    /**
     * 닉네임 중복 확인
     */
    boolean existsByNickName(@Param("nickName") String nickName);
} 