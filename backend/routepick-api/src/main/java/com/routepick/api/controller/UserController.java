package com.routepick.api.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import com.routepick.api.security.CustomUserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.routepick.api.dto.user.SimpleProfileDTO;
import com.routepick.common.dto.ResponseDTO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
@Tag(name = "사용자", description = "사용자 관련 API")
public class UserController {

    @GetMapping("/profile/simple")
    @Operation(
        summary = "마이페이지 간단한 프로필 정보 조회", 
        description = "마이페이지 카테고리에 표시되는 프로필 이미지와 닉네임만 조회합니다. JWT 토큰에서 직접 정보를 추출하므로 DB 조회가 없습니다.",
        security = @SecurityRequirement(name = "Bearer Auth")
    )
    @ApiResponses({
        @ApiResponse(
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
                          "data": {
                            "userName": "climber123",
                            "profileImageUrl": "https://example.com/profile.jpg"
                          },
                          "message": "프로필 정보 조회 성공"
                        }
                        """
                    )
                }
            )
        ),
        @ApiResponse(responseCode = "401", description = "인증되지 않은 사용자"),
        @ApiResponse(responseCode = "500", description = "서버 내부 오류")
    })
    public ResponseEntity<ResponseDTO<SimpleProfileDTO>> getSimpleProfile(
        @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails userDetails) {
            
            // CustomUserDetails에서 직접 정보 추출 (DB 조회 없음)
            SimpleProfileDTO profile = SimpleProfileDTO.builder()
                .userName(userDetails.getUserName()) // 닉네임
                .profileImageUrl(userDetails.getProfileImageUrl()) // 프로필 이미지
                .build();

            return ResponseEntity.ok(ResponseDTO.success(profile, "프로필 정보 조회 성공"));
    }
    
}
