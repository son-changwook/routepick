package com.routepick.api.controller;

import com.routepick.api.service.file.FileService;
import com.routepick.common.dto.ApiResponse;
import com.routepick.common.exception.FileException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Pattern;

/**
 * 파일 업로드 및 서빙 컨트롤러
 * 프로필 이미지 파일을 업로드하고 서빙합니다.
 * 보안 강화: 인증, Path Traversal 방지, 파일 확장자 검증
 */
@Slf4j
@Tag(name = "파일", description = "파일 업로드 및 서빙 API")
@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
public class FileController {

    private static final String UPLOAD_DIR = "uploads/profiles/";
    private static final Pattern SAFE_FILENAME_PATTERN = Pattern.compile("^[a-zA-Z0-9._-]+$");
    private final FileService fileService;

    @Operation(
        summary = "파일 업로드",
        description = "프로필 이미지 파일을 업로드합니다. JPG, PNG, GIF 형식을 지원합니다.",
        security = @SecurityRequirement(name = "Bearer Auth")
    )
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "파일 업로드 성공",
            content = @Content(mediaType = "application/json")
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "잘못된 요청 (파일 형식 오류, 크기 초과 등)",
            content = @Content(mediaType = "application/json")
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "401",
            description = "인증되지 않은 사용자",
            content = @Content(mediaType = "application/json")
        )
    })
    @PostMapping("/upload")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<String>> uploadFile(
            @Parameter(description = "업로드할 이미지 파일")
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal com.routepick.api.security.CustomUserDetails userDetails) {
        
        log.info("파일 업로드 요청: userId={}, filename={}", 
                userDetails.getUserId(), file.getOriginalFilename());
        
        try {
            // 파일 업로드 처리
            String fileUrl = fileService.uploadProfileImage(file);
            
            log.info("파일 업로드 완료: userId={}, fileUrl={}", userDetails.getUserId(), fileUrl);
            
            return ResponseEntity.ok(ApiResponse.success(
                "파일이 성공적으로 업로드되었습니다.", 
                fileUrl
            ));
            
        } catch (FileException e) {
            log.error("파일 업로드 실패: userId={}, error={}", userDetails.getUserId(), e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("파일 업로드 중 예상치 못한 오류: userId={}, error={}", 
                    userDetails.getUserId(), e.getMessage(), e);
            throw FileException.fileUploadFailed(e.getMessage(), e);
        }
    }

    @Operation(
        summary = "프로필 이미지 서빙",
        description = "업로드된 프로필 이미지 파일을 서빙합니다. 보안 강화: Path Traversal 방지, 안전한 파일명 검증"
    )
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "이미지 파일 반환",
            content = @Content(mediaType = "image/*")
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "잘못된 파일명",
            content = @Content(mediaType = "application/json")
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "파일을 찾을 수 없음",
            content = @Content(mediaType = "application/json")
        )
    })
    @GetMapping("/profiles/{filename}")
    public ResponseEntity<Resource> serveProfileImage(
            @Parameter(description = "파일명")
            @PathVariable String filename) {
        
        try {
            // 보안 검증: 안전한 파일명인지 확인
            if (!isSafeFilename(filename)) {
                log.warn("안전하지 않은 파일명 접근 시도: {}", filename);
                return ResponseEntity.badRequest().build();
            }
            
            // Path Traversal 방지: 정규화된 경로 사용
            Path uploadPath = Paths.get(UPLOAD_DIR).toAbsolutePath().normalize();
            Path filePath = uploadPath.resolve(filename).normalize();
            
            // 경로 검증: 업로드 디렉토리 밖으로 나가지 않는지 확인
            if (!filePath.startsWith(uploadPath)) {
                log.warn("Path Traversal 공격 시도: {}", filename);
                return ResponseEntity.badRequest().build();
            }
            
            Resource resource = new UrlResource(filePath.toUri());
            
            if (resource.exists() && resource.isReadable()) {
                // Content-Type 동적 설정
                MediaType mediaType = getMediaType(filename);
                
                return ResponseEntity.ok()
                        .contentType(mediaType)
                        .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + filename + "\"")
                        .body(resource);
            } else {
                log.warn("파일을 찾을 수 없습니다: {}", filename);
                return ResponseEntity.notFound().build();
            }
            
        } catch (IOException e) {
            log.error("파일 서빙 중 오류 발생: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * 안전한 파일명인지 검증
     * @param filename 파일명
     * @return 안전한 파일명이면 true
     */
    private boolean isSafeFilename(String filename) {
        if (filename == null || filename.trim().isEmpty()) {
            return false;
        }
        
        // 특수문자나 경로 구분자 포함 여부 확인
        if (filename.contains("..") || filename.contains("/") || filename.contains("\\")) {
            return false;
        }
        
        // 안전한 파일명 패턴 검증
        return SAFE_FILENAME_PATTERN.matcher(filename).matches();
    }
    
    /**
     * 파일 확장자에 따른 MediaType 반환
     * @param filename 파일명
     * @return MediaType
     */
    private MediaType getMediaType(String filename) {
        String extension = filename.substring(filename.lastIndexOf(".") + 1).toLowerCase();
        
        return switch (extension) {
            case "jpg", "jpeg" -> MediaType.IMAGE_JPEG;
            case "png" -> MediaType.IMAGE_PNG;
            case "gif" -> MediaType.IMAGE_GIF;
            case "webp" -> MediaType.valueOf("image/webp");
            default -> MediaType.APPLICATION_OCTET_STREAM;
        };
    }
} 