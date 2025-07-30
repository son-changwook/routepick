package com.routepick.api.service.file;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@Slf4j
@Service
public class FileService {

    private static final String UPLOAD_DIR = "uploads/profiles/";
    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024; // 5MB
    private static final String[] ALLOWED_EXTENSIONS = {".jpg", ".jpeg", ".png", ".gif"};

    /**
     * 프로필 이미지 업로드
     * @param file 업로드할 이미지 파일
     * @return 업로드된 파일의 URL
     */
    public String uploadProfileImage(MultipartFile file) {
        try {
            // 1. 파일 검증
            validateImageFile(file);
            
            // 2. 업로드 디렉토리 생성
            Path uploadPath = Paths.get(UPLOAD_DIR);
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }
            
            // 3. 파일명 생성 (UUID + 원본 확장자)
            String originalFilename = file.getOriginalFilename();
            String extension = getFileExtension(originalFilename);
            String filename = UUID.randomUUID().toString() + extension;
            
            // 4. 파일 저장
            Path filePath = uploadPath.resolve(filename);
            Files.copy(file.getInputStream(), filePath);
            
            // 5. URL 반환 (실제 환경에서는 CDN이나 클라우드 스토리지 URL로 변경)
            String fileUrl = "/api/files/profiles/" + filename;
            
            log.info("프로필 이미지 업로드 완료: {}", fileUrl);
            
            return fileUrl;
            
        } catch (IOException e) {
            log.error("파일 업로드 실패: {}", e.getMessage(), e);
            throw new RuntimeException("파일 업로드에 실패했습니다.", e);
        }
    }

    /**
     * 이미지 파일 검증
     */
    private void validateImageFile(MultipartFile file) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("업로드할 파일이 없습니다.");
        }
        
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("파일 크기는 5MB 이하여야 합니다.");
        }
        
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isEmpty()) {
            throw new IllegalArgumentException("파일명이 없습니다.");
        }
        
        String extension = getFileExtension(originalFilename).toLowerCase();
        boolean isValidExtension = false;
        for (String allowedExt : ALLOWED_EXTENSIONS) {
            if (allowedExt.equals(extension)) {
                isValidExtension = true;
                break;
            }
        }
        
        if (!isValidExtension) {
            throw new IllegalArgumentException("지원하지 않는 파일 형식입니다. (jpg, jpeg, png, gif만 가능)");
        }
    }

    /**
     * 파일 확장자 추출
     */
    private String getFileExtension(String filename) {
        int lastDotIndex = filename.lastIndexOf('.');
        if (lastDotIndex == -1) {
            throw new IllegalArgumentException("파일 확장자가 없습니다.");
        }
        return filename.substring(lastDotIndex);
    }
} 