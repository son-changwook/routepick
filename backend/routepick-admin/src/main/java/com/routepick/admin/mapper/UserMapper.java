package com.routepick.admin.mapper;

import com.routepick.common.domain.user.User;
import com.routepick.common.enums.UserType;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

@Mapper
public interface UserMapper {
    
    /**
     * 사용자명으로 사용자 조회 (모든 타입)
     */
    Optional<User> findByUsername(@Param("username") String username);
    
    /**
     * 사용자명과 사용자 타입으로 사용자 조회
     */
    Optional<User> findByUsernameAndUserType(@Param("username") String username, @Param("userType") UserType userType);
    
    /**
     * 이메일로 사용자 조회
     */
    Optional<User> findByEmail(@Param("email") String email);
    
    /**
     * 사용자 ID로 사용자 조회
     */
    Optional<User> findById(@Param("userId") Long userId);
    
    /**
     * 모든 사용자 목록 조회 (관리자용)
     */
    List<User> findAllUsers();
    
    /**
     * 사용자 타입으로 사용자 목록 조회
     */
    List<User> findByUserType(@Param("userType") UserType userType);
    
    /**
     * 관리자 목록 조회
     */
    List<User> findAdmins();
    
    /**
     * 헬스장 관리자 목록 조회
     */
    List<User> findGymAdmins();
    
    /**
     * 사용자 상태별 목록 조회
     */
    List<User> findByUserStatus(@Param("userStatus") String userStatus);
    
    /**
     * 사용자 저장
     */
    int insertUser(User user);
    
    /**
     * 사용자 정보 수정
     */
    int updateUser(User user);
    
    /**
     * 사용자 삭제 (비활성화)
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
     * 사용자 통계 조회
     */
    int countByUserType(@Param("userType") UserType userType);
    
    /**
     * 사용자 상태별 통계 조회
     */
    int countByUserStatus(@Param("userStatus") String userStatus);
} 