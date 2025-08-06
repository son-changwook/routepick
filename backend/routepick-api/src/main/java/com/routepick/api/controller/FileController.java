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
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Parameter;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 파일 업로드 및 서빙 컨트롤러
 * 프로필 이미지 파일을 업로드하고 서빙합니다.
 */
@Slf4j
@Tag(name = "파일", description = "파일 업로드 및 서빙 API")
@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
public class FileController {

    private static final String UPLOAD_DIR = "uploads/profiles/";
    private final FileService fileService;

    @Operation(
        summary = "파일 업로드",
        description = "프로필 이미지 파일을 업로드합니다. JPG, PNG, GIF 형식을 지원합니다."
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
        )
    })
    @PostMapping("/upload")
    public ResponseEntity<ApiResponse<String>> uploadFile(
            @Parameter(description = "업로드할 이미지 파일")
            @RequestParam("file") MultipartFile file) {
        
        log.info("파일 업로드 요청: {}", file.getOriginalFilename());
        
        try {
            // 파일 업로드 처리
            String fileUrl = fileService.uploadProfileImage(file);
            
            log.info("파일 업로드 완료: {}", fileUrl);
            
            return ResponseEntity.ok(ApiResponse.success(
                "파일이 성공적으로 업로드되었습니다.", 
                fileUrl
            ));
            
        } catch (FileException e) {
            log.error("파일 업로드 실패: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("파일 업로드 중 예상치 못한 오류: {}", e.getMessage(), e);
            throw FileException.fileUploadFailed(e.getMessage(), e);
        }
    }

    @Operation(
        summary = "프로필 이미지 서빙",
        description = "업로드된 프로필 이미지 파일을 서빙합니다."
    )
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "이미지 파일 반환",
            content = @Content(mediaType = "image/*")
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
            Path filePath = Paths.get(UPLOAD_DIR).resolve(filename);
            Resource resource = new UrlResource(filePath.toUri());
            
            if (resource.exists() && resource.isReadable()) {
                return ResponseEntity.ok()
                        .contentType(MediaType.IMAGE_JPEG) // 기본값, 실제로는 파일 확장자에 따라 결정
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
} 