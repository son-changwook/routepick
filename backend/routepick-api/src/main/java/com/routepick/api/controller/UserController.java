package com.routepick.api.controller;

import com.routepick.api.dto.user.PersonalInfoUpdateRequest;
import com.routepick.api.dto.user.UserInfoResponse;
import com.routepick.api.service.user.UserService;
import com.routepick.common.dto.ApiResponse;
import com.routepick.api.security.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * 사용자 정보 컨트롤러
 * users 테이블의 기본 회원 정보를 관리
 */
@Slf4j
@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
@Tag(name = "사용자 정보", description = "사용자 정보 관리 API")
public class UserController {
    
    private final UserService userService;
    
    /**
     * 사용자 정보 조회
     * @param userDetails 인증된 사용자 정보
     * @return 사용자 정보
     */
    @GetMapping("/info")
    @Operation(
        summary = "사용자 정보 조회",
        description = "현재 로그인한 사용자의 기본 정보를 조회합니다."
    )
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "사용자 정보 조회 성공",
            content = @Content(
                schema = @Schema(implementation = UserInfoResponse.class),
                mediaType = "application/json"
            )
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "401",
            description = "인증 실패"
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "사용자를 찾을 수 없음"
        )
    })
    public ResponseEntity<com.routepick.common.dto.ApiResponse<UserInfoResponse>> getUserInfo(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails) {
        
        log.info("사용자 정보 조회 요청: userId={}", userDetails.getUserId());
        
        UserInfoResponse userInfo = userService.getUserInfo(userDetails.getUserId());
        
        log.info("사용자 정보 조회 완료: userId={}", userDetails.getUserId());
        
        return ResponseEntity.ok(com.routepick.common.dto.ApiResponse.success("사용자 정보 조회 성공", userInfo));
    }
    
    /**
     * 사용자 정보 수정
     * @param request 수정 요청
     * @param userDetails 인증된 사용자 정보
     * @return 수정된 사용자 정보
     */
    @PutMapping("/info")
    @Operation(
        summary = "사용자 정보 수정",
        description = "현재 로그인한 사용자의 기본 정보를 수정합니다."
    )
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "사용자 정보 수정 성공",
            content = @Content(
                schema = @Schema(implementation = UserInfoResponse.class),
                mediaType = "application/json"
            )
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "잘못된 요청"
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "401",
            description = "인증 실패"
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "사용자를 찾을 수 없음"
        )
    })
    public ResponseEntity<com.routepick.common.dto.ApiResponse<UserInfoResponse>> updateUserInfo(
            @Valid @RequestBody PersonalInfoUpdateRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails) {
        
        log.info("사용자 정보 수정 요청: userId={}", userDetails.getUserId());
        
        UserInfoResponse userInfo = userService.updateUserInfo(userDetails.getUserId(), request);
        
        log.info("사용자 정보 수정 완료: userId={}", userDetails.getUserId());
        
        return ResponseEntity.ok(com.routepick.common.dto.ApiResponse.success("사용자 정보 수정 성공", userInfo));
    }
}
