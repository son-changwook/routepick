package com.routepick.api.mapper;

import com.routepick.common.domain.token.ApiToken;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

@Mapper
public interface ApiTokenMapper {
    
    /**
     * 토큰 저장
     */
    int insertToken(ApiToken token);
    
    /**
     * 토큰으로 조회
     */
    Optional<ApiToken> findByToken(@Param("token") String token);
    
    /**
     * 사용자의 활성 토큰 조회
     */
    List<ApiToken> findActiveTokensByUserId(@Param("userId") Long userId);
    
    /**
     * 사용자의 특정 타입 토큰 조회
     */
    Optional<ApiToken> findByUserIdAndType(
        @Param("userId") Long userId, 
        @Param("tokenType") ApiToken.TokenType tokenType
    );
    
    /**
     * 토큰 만료 처리
     */
    int revokeToken(@Param("tokenId") Long tokenId);
    
    /**
     * 사용자의 모든 토큰 만료 처리
     */
    int revokeAllTokensByUserId(@Param("userId") Long userId);
    
    /**
     * 만료된 토큰 삭제
     */
    int deleteExpiredTokens();
} 