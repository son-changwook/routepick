package com.routepick.api.mapper;

import java.util.Optional;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.routepick.common.domain.user.UserDetails;

/**
 * 사용자 상세 정보 매퍼 인터페이스
 * 클라이밍 관련 정보와 소셜 정보를 관리합니다.
 */
@Mapper
public interface UserDetailsMapper {
    
    /**
     * 사용자 ID로 상세 정보 조회
     * @param userId 사용자 ID
     * @return 사용자 상세 정보 (Optional)
     */
    Optional<UserDetails> findByUserId(@Param("userId") Long userId);
    
    /**
     * 사용자 상세 정보 저장
     * @param userDetails 사용자 상세 정보
     * @return 저장된 레코드 수
     */
    int insertUserDetails(UserDetails userDetails);
    
    /**
     * 사용자 상세 정보 수정
     * @param userDetails 사용자 상세 정보
     * @return 수정된 레코드 수
     */
    int updateUserDetails(UserDetails userDetails);
    
    /**
     * 사용자 상세 정보 삭제
     * @param userId 사용자 ID
     * @return 삭제된 레코드 수
     */
    int deleteUserDetails(@Param("userId") Long userId);
    
    /**
     * 닉네임으로 사용자 상세 정보 조회
     * @param nickName 닉네임
     * @return 사용자 상세 정보 (Optional)
     */
    Optional<UserDetails> findByNickName(@Param("nickName") String nickName);
    
    /**
     * 닉네임 중복 확인
     * @param nickName 확인할 닉네임
     * @return 중복된 경우 true, 중복되지 않은 경우 false
     */
    boolean existsByNickName(@Param("nickName") String nickName);
    
    /**
     * 사용자 ID로 닉네임 조회
     * @param userId 사용자 ID
     * @return 닉네임 (Optional)
     */
    Optional<String> findNickNameByUserId(@Param("userId") Long userId);
} 