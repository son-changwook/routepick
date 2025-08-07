package com.routepick.api.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import com.routepick.api.security.CustomUserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.routepick.api.dto.user.SimpleProfileDTO;
import com.routepick.api.dto.user.PersonalInfoResponse;
import com.routepick.api.dto.user.PersonalInfoUpdateRequest;
import com.routepick.api.service.user.UserService;
import com.routepick.api.util.InputSanitizer;
import com.routepick.api.util.RateLimitHelper;
import com.routepick.api.util.SecureLogger;
import com.routepick.common.dto.ApiResponse;
import com.routepick.common.dto.ResponseDTO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

@Slf4j
@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
@Tag(name = "사용자", description = "사용자 관련 API")
public class UserController {

    private final UserService userService;
    private final RateLimitHelper rateLimitHelper;

    @GetMapping("/profile/simple")
    @Operation(
        summary = "간단한 프로필 정보 조회", 
        description = "마이페이지에 표시되는 기본 프로필 정보(닉네임, 프로필 이미지)를 조회합니다. JWT 토큰에서 직접 정보를 추출하므로 DB 조회가 없습니다.",
        security = @SecurityRequirement(name = "Bearer Auth")
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200", 
            description = "프로필 정보 조회 성공",
            content = @Content(
                schema = @Schema(implementation = ResponseDTO.class),
                examples = {
                    @io.swagger.v3.oas.annotations.media.ExampleObject(
                        name = "성공 예시",
                        value = """
                        {
                          "success": true,
                          "code": 200,
                          "message": "프로필 정보 조회 성공",
                          "data": {
                            "userName": "climber123",
                            "profileImageUrl": "https://example.com/profile.jpg"
                          },
                          "timestamp": "2024-01-01T12:00:00"
                        }
                        """
                    )
                }
            )
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증되지 않은 사용자"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "서버 내부 오류")
    })
    public ResponseEntity<ResponseDTO<SimpleProfileDTO>> getSimpleProfile(
        @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails) {
        
        log.info("간단한 프로필 정보 조회 요청: userId={}", userDetails.getUserId());
        
        // JWT 토큰에서 직접 정보 추출 (DB 조회 없음)
        SimpleProfileDTO profile = SimpleProfileDTO.builder()
                .userName(userDetails.getDisplayName())
                .profileImageUrl(userDetails.getProfileImageUrl())
                .build();
        
        log.info("간단한 프로필 정보 조회 완료: userId={}", userDetails.getUserId());
        
        return ResponseEntity.ok(ResponseDTO.success(profile, "프로필 정보 조회 성공"));
    }

    @GetMapping("/profile/detail")
    @Operation(
        summary = "상세 프로필 정보 조회",
        description = "사용자의 상세 프로필 정보를 조회합니다. 개인정보와 클라이밍 관련 정보를 포함합니다.",
        security = @SecurityRequirement(name = "Bearer Auth")
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200", 
            description = "상세 프로필 정보 조회 성공",
            content = @Content(
                schema = @Schema(implementation = PersonalInfoResponse.class),
                examples = {
                    @io.swagger.v3.oas.annotations.media.ExampleObject(
                        name = "성공 예시",
                        value = """
                        {
                          "success": true,
                          "code": 200,
                          "message": "상세 프로필 정보 조회 성공",
                          "data": {
                            "email": "user@example.com",
                            "userName": "climber123",
                            "phone": "010-1234-5678",
                            "profileImageUrl": "/api/files/profiles/user123.jpg",
                            "birthDate": "1990-01-01",
                            "address": "서울시 강남구",
                            "detailAddress": "123-45",
                            "emergencyContact": "010-9876-5432"
                          },
                          "timestamp": "2024-01-01T12:00:00"
                        }
                        """
                    )
                }
            )
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증되지 않은 사용자"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "사용자를 찾을 수 없음"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "429", description = "요청 한도 초과"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "서버 내부 오류")
    })
    public ResponseEntity<ApiResponse<PersonalInfoResponse>> getPersonalInfo(
        @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails,
        HttpServletRequest httpRequest) {
            
        log.info("상세 프로필 정보 조회 요청 시작: userId={}", userDetails.getUserId());
        
        // Rate Limit 체크 (IP + 사용자 ID)
        rateLimitHelper.checkEndpointRateLimit(httpRequest, userDetails.getUserId().toString());
        
        // 보안 이벤트 로깅
        SecureLogger.logSecurityEvent("상세 프로필 정보 조회 시도: userId={}, ip={}", 
            userDetails.getUserId(), rateLimitHelper.getClientIpAddress(httpRequest));
        
        // 상세 프로필 정보 조회
        PersonalInfoResponse personalInfo = userService.getPersonalInfo(userDetails.getUserId());
        
        log.info("상세 프로필 정보 조회 완료: userId={}", userDetails.getUserId());
        
        return ResponseEntity.ok(ApiResponse.success("상세 프로필 정보 조회 성공", personalInfo));
    }

    @PutMapping("/profile/detail")
    @Operation(
        summary = "상세 프로필 정보 수정",
        description = "사용자의 상세 프로필 정보를 수정합니다. 개인정보와 클라이밍 관련 정보를 포함합니다.",
        security = @SecurityRequirement(name = "Bearer Auth")
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200", 
            description = "상세 프로필 정보 수정 성공",
            content = @Content(
                schema = @Schema(implementation = PersonalInfoResponse.class),
                examples = {
                    @io.swagger.v3.oas.annotations.media.ExampleObject(
                        name = "성공 예시",
                        value = """
                        {
                          "success": true,
                          "code": 200,
                          "message": "상세 프로필 정보 수정 성공",
                          "data": {
                            "email": "user@example.com",
                            "userName": "climber123",
                            "phone": "010-1234-5678",
                            "profileImageUrl": "/api/files/profiles/user123.jpg",
                            "birthDate": "1990-01-01",
                            "address": "서울시 강남구",
                            "detailAddress": "123-45",
                            "emergencyContact": "010-9876-5432"
                          },
                          "timestamp": "2024-01-01T12:00:00"
                        }
                        """
                    )
                }
            )
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "잘못된 요청 데이터"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증되지 않은 사용자"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "사용자를 찾을 수 없음"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "429", description = "요청 한도 초과"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "서버 내부 오류")
    })
    public ResponseEntity<ApiResponse<PersonalInfoResponse>> updatePersonalInfo(
        @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails,
        @Valid @RequestBody PersonalInfoUpdateRequest request,
        HttpServletRequest httpRequest) {
            
        log.info("상세 프로필 정보 수정 요청 시작: userId={}", userDetails.getUserId());
        
        // Rate Limit 체크 (IP + 사용자 ID)
        rateLimitHelper.checkEndpointRateLimit(httpRequest, userDetails.getUserId().toString());
        
        // 보안 이벤트 로깅
        SecureLogger.logSecurityEvent("상세 프로필 정보 수정 시도: userId={}, ip={}", 
            userDetails.getUserId(), rateLimitHelper.getClientIpAddress(httpRequest));
        
        // 입력값 정제
        String sanitizedUserName = InputSanitizer.sanitizeInput(request.getUserName());
        String sanitizedPhone = InputSanitizer.sanitizeInput(request.getPhone());
        String sanitizedAddress = InputSanitizer.sanitizeInput(request.getAddress());
        String sanitizedDetailAddress = InputSanitizer.sanitizeInput(request.getDetailAddress());
        String sanitizedEmergencyContact = InputSanitizer.sanitizeInput(request.getEmergencyContact());
        
        // Request 객체 업데이트
        request.setUserName(sanitizedUserName);
        request.setPhone(sanitizedPhone);
        request.setAddress(sanitizedAddress);
        request.setDetailAddress(sanitizedDetailAddress);
        request.setEmergencyContact(sanitizedEmergencyContact);
        
        // 상세 프로필 정보 수정
        PersonalInfoResponse personalInfo = userService.updatePersonalInfo(userDetails.getUserId(), request);
        
        log.info("상세 프로필 정보 수정 완료: userId={}", userDetails.getUserId());
        
        return ResponseEntity.ok(ApiResponse.success("상세 프로필 정보 수정 성공", personalInfo));
    }
}
