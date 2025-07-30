package com.routepick.api.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 파일 서빙 컨트롤러
 * 프로필 이미지 파일을 서빙합니다.
 */
@Slf4j
@RestController
@RequestMapping("/api/files")
public class FileController {

    private static final String UPLOAD_DIR = "uploads/profiles/";

    /**
     * 프로필 이미지 파일 서빙
     * @param filename 파일명
     * @return 이미지 파일
     */
    @GetMapping("/profiles/{filename}")
    public ResponseEntity<Resource> serveProfileImage(@PathVariable String filename) {
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