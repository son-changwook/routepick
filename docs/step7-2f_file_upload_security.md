# Step 7-2f: 파일 업로드 보안 강화

## 📋 구현 목표
프로필 이미지 업로드의 보안 취약점을 해결하는 종합적인 파일 업로드 보안 시스템 구현:
1. **파일 검증** - 확장자, MIME 타입, 크기 검증
2. **악성코드 방지** - 바이러스 스캔, 메타데이터 제거
3. **이미지 보안** - 이미지 내용 검증, 재인코딩
4. **저장소 보안** - 안전한 파일명, 경로 관리

---

## 🛡️ FileUploadSecurityService 구현

### 📁 파일 위치
```
src/main/java/com/routepick/service/security/FileUploadSecurityService.java
```

### 📝 구현 코드
```java
package com.routepick.service.security;

import com.routepick.exception.file.FileUploadSecurityException;
import com.routepick.exception.file.InvalidFileFormatException;
import com.routepick.exception.file.FileSizeLimitExceededException;
import com.routepick.exception.file.MaliciousFileDetectedException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 파일 업로드 보안 검증 서비스
 * 
 * @author RoutePickProj
 * @since 1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileUploadSecurityService {

    private static final Set<String> ALLOWED_EXTENSIONS = 
        Set.of("jpg", "jpeg", "png", "gif", "webp");
    
    private static final Set<String> ALLOWED_MIME_TYPES = 
        Set.of("image/jpeg", "image/png", "image/gif", "image/webp");
    
    private static final Set<String> DANGEROUS_EXTENSIONS = 
        Set.of("exe", "bat", "cmd", "com", "pif", "scr", "vbs", "js", 
               "jar", "php", "asp", "jsp", "sh", "ps1");
    
    private static final Pattern SAFE_FILENAME_PATTERN = 
        Pattern.compile("^[a-zA-Z0-9._-]+$");
    
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB
    private static final int MAX_IMAGE_DIMENSION = 4096; // 4K 해상도
    
    @Value("${file.upload.temp-dir:/tmp/routepick}")
    private String tempDirectory;
    
    private final Tika tika = new Tika();
    private final VirusScanService virusScanService;
    private final ImageProcessingService imageProcessingService;

    /**
     * 파일 업로드 종합 보안 검증
     */
    public SecureFileInfo validateAndSecureFile(MultipartFile file, String uploadType) {
        if (file == null || file.isEmpty()) {
            throw new FileUploadSecurityException("업로드할 파일이 없습니다");
        }

        log.info("Starting file security validation: originalName={}, size={}, type={}", 
                file.getOriginalFilename(), file.getSize(), uploadType);

        try {
            // 1. 기본 파일 정보 검증
            validateBasicFileInfo(file);
            
            // 2. 파일 크기 검증
            validateFileSize(file);
            
            // 3. 파일 확장자 검증
            validateFileExtension(file);
            
            // 4. MIME 타입 검증
            validateMimeType(file);
            
            // 5. 파일 내용 검증 (Magic Number)
            validateFileContent(file);
            
            // 6. 이미지 파일 특화 검증
            validateImageContent(file);
            
            // 7. 악성코드 스캔
            scanForMalware(file);
            
            // 8. 안전한 파일 생성
            return createSecureFile(file, uploadType);
            
        } catch (Exception e) {
            log.error("File security validation failed: file={}, error={}", 
                     file.getOriginalFilename(), e.getMessage());
            throw new FileUploadSecurityException("파일 보안 검증 실패: " + e.getMessage(), e);
        }
    }

    /**
     * 기본 파일 정보 검증
     */
    private void validateBasicFileInfo(MultipartFile file) {
        String originalFilename = file.getOriginalFilename();
        
        if (originalFilename == null || originalFilename.trim().isEmpty()) {
            throw new InvalidFileFormatException("파일명이 올바르지 않습니다");
        }
        
        // 파일명 길이 제한
        if (originalFilename.length() > 255) {
            throw new InvalidFileFormatException("파일명이 너무 깁니다 (최대 255자)");
        }
        
        // 위험한 문자 검사
        if (containsDangerousCharacters(originalFilename)) {
            throw new InvalidFileFormatException("파일명에 허용되지 않는 문자가 포함되어 있습니다");
        }
        
        // 다중 확장자 검사 (.tar.gz, .php.jpg 등)
        if (hasMultipleExtensions(originalFilename)) {
            throw new InvalidFileFormatException("다중 확장자는 허용되지 않습니다");
        }
    }

    /**
     * 파일 크기 검증
     */
    private void validateFileSize(MultipartFile file) {
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new FileSizeLimitExceededException(
                String.format("파일 크기가 제한을 초과합니다 (최대: %dMB, 현재: %.2fMB)",
                    MAX_FILE_SIZE / (1024 * 1024), 
                    (double) file.getSize() / (1024 * 1024)));
        }
        
        if (file.getSize() == 0) {
            throw new InvalidFileFormatException("빈 파일은 업로드할 수 없습니다");
        }
    }

    /**
     * 파일 확장자 검증
     */
    private void validateFileExtension(MultipartFile file) {
        String filename = file.getOriginalFilename();
        String extension = getFileExtension(filename).toLowerCase();
        
        if (extension.isEmpty()) {
            throw new InvalidFileFormatException("파일 확장자가 없습니다");
        }
        
        if (DANGEROUS_EXTENSIONS.contains(extension)) {
            throw new MaliciousFileDetectedException("위험한 파일 확장자입니다: " + extension);
        }
        
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new InvalidFileFormatException(
                "지원하지 않는 파일 형식입니다. 지원 형식: " + ALLOWED_EXTENSIONS);
        }
    }

    /**
     * MIME 타입 검증
     */
    private void validateMimeType(MultipartFile file) throws IOException {
        String declaredMimeType = file.getContentType();
        String detectedMimeType;
        
        try (InputStream inputStream = file.getInputStream()) {
            detectedMimeType = tika.detect(inputStream, file.getOriginalFilename());
        }
        
        log.debug("MIME type validation: declared={}, detected={}", 
                 declaredMimeType, detectedMimeType);
        
        // 선언된 MIME 타입 검증
        if (declaredMimeType == null || !ALLOWED_MIME_TYPES.contains(declaredMimeType)) {
            throw new InvalidFileFormatException("허용되지 않는 MIME 타입입니다: " + declaredMimeType);
        }
        
        // 실제 파일 내용과 MIME 타입 일치 검증
        if (!ALLOWED_MIME_TYPES.contains(detectedMimeType)) {
            throw new MaliciousFileDetectedException(
                "파일 내용이 확장자와 일치하지 않습니다. 감지된 타입: " + detectedMimeType);
        }
        
        // Polyglot 파일 탐지 (선언된 타입과 실제 타입이 다른 경우)
        if (!declaredMimeType.equals(detectedMimeType)) {
            log.warn("MIME type mismatch detected: declared={}, detected={}, file={}", 
                    declaredMimeType, detectedMimeType, file.getOriginalFilename());
            throw new MaliciousFileDetectedException("파일 타입 불일치가 감지되었습니다");
        }
    }

    /**
     * 파일 내용 검증 (Magic Number)
     */
    private void validateFileContent(MultipartFile file) throws IOException {
        byte[] header = new byte[20]; // 첫 20바이트 읽기
        
        try (InputStream inputStream = file.getInputStream()) {
            int bytesRead = inputStream.read(header);
            if (bytesRead < 4) {
                throw new InvalidFileFormatException("파일이 손상되었거나 크기가 너무 작습니다");
            }
        }
        
        // Magic Number 검증
        if (!isValidImageMagicNumber(header)) {
            throw new MaliciousFileDetectedException("유효하지 않은 이미지 파일입니다");
        }
        
        // 의심스러운 패턴 검사
        if (containsSuspiciousPatterns(header)) {
            throw new MaliciousFileDetectedException("의심스러운 파일 패턴이 감지되었습니다");
        }
    }

    /**
     * 이미지 파일 특화 검증
     */
    private void validateImageContent(MultipartFile file) throws IOException {
        BufferedImage image;
        
        try (InputStream inputStream = file.getInputStream()) {
            image = ImageIO.read(inputStream);
        }
        
        if (image == null) {
            throw new InvalidFileFormatException("유효한 이미지 파일이 아닙니다");
        }
        
        // 이미지 크기 검증
        if (image.getWidth() > MAX_IMAGE_DIMENSION || image.getHeight() > MAX_IMAGE_DIMENSION) {
            throw new FileSizeLimitExceededException(
                String.format("이미지 크기가 너무 큽니다 (최대: %dx%d, 현재: %dx%d)",
                    MAX_IMAGE_DIMENSION, MAX_IMAGE_DIMENSION, 
                    image.getWidth(), image.getHeight()));
        }
        
        // 최소 크기 검증
        if (image.getWidth() < 32 || image.getHeight() < 32) {
            throw new InvalidFileFormatException("이미지 크기가 너무 작습니다 (최소: 32x32)");
        }
        
        // 비정상적인 종횡비 검사
        double aspectRatio = (double) image.getWidth() / image.getHeight();
        if (aspectRatio > 10.0 || aspectRatio < 0.1) {
            throw new InvalidFileFormatException("비정상적인 이미지 종횡비입니다");
        }
    }

    /**
     * 악성코드 스캔
     */
    private void scanForMalware(MultipartFile file) throws IOException {
        // ClamAV 또는 다른 바이러스 스캔 엔진 연동
        boolean isMalwareDetected = virusScanService.scanFile(file);
        
        if (isMalwareDetected) {
            log.error("Malware detected in uploaded file: {}", file.getOriginalFilename());
            throw new MaliciousFileDetectedException("악성 파일이 감지되었습니다");
        }
    }

    /**
     * 안전한 파일 생성
     */
    private SecureFileInfo createSecureFile(MultipartFile file, String uploadType) throws IOException {
        // 안전한 파일명 생성
        String secureFilename = generateSecureFilename(file.getOriginalFilename());
        String extension = getFileExtension(file.getOriginalFilename());
        
        // 임시 파일 생성
        Path tempPath = Path.of(tempDirectory, secureFilename);
        Files.createDirectories(tempPath.getParent());
        
        // 이미지 재인코딩 (EXIF 데이터 제거 및 보안 강화)
        byte[] secureImageData = imageProcessingService.reencodeImage(file, extension);
        
        // 임시 파일에 저장
        Files.write(tempPath, secureImageData);
        
        return SecureFileInfo.builder()
            .originalFilename(file.getOriginalFilename())
            .secureFilename(secureFilename)
            .contentType(file.getContentType())
            .size(secureImageData.length)
            .extension(extension)
            .tempPath(tempPath.toString())
            .uploadType(uploadType)
            .build();
    }

    /**
     * 안전한 파일명 생성
     */
    private String generateSecureFilename(String originalFilename) {
        String extension = getFileExtension(originalFilename);
        String uuid = UUID.randomUUID().toString().replace("-", "");
        long timestamp = System.currentTimeMillis();
        
        return String.format("%d_%s.%s", timestamp, uuid, extension);
    }

    /**
     * 파일 확장자 추출
     */
    private String getFileExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf('.') + 1);
    }

    /**
     * 위험한 문자 포함 여부 검사
     */
    private boolean containsDangerousCharacters(String filename) {
        return filename.contains("..") || 
               filename.contains("/") || 
               filename.contains("\\") || 
               filename.contains("<") || 
               filename.contains(">") || 
               filename.contains("|") || 
               filename.contains("?") || 
               filename.contains("*") ||
               filename.contains(":") ||
               filename.contains("\"");
    }

    /**
     * 다중 확장자 검사
     */
    private boolean hasMultipleExtensions(String filename) {
        String[] parts = filename.split("\\.");
        return parts.length > 2;
    }

    /**
     * 유효한 이미지 Magic Number 검증
     */
    private boolean isValidImageMagicNumber(byte[] header) {
        // JPEG: FF D8 FF
        if (header.length >= 3 && 
            (header[0] & 0xFF) == 0xFF && 
            (header[1] & 0xFF) == 0xD8 && 
            (header[2] & 0xFF) == 0xFF) {
            return true;
        }
        
        // PNG: 89 50 4E 47 0D 0A 1A 0A
        if (header.length >= 8 &&
            (header[0] & 0xFF) == 0x89 &&
            (header[1] & 0xFF) == 0x50 &&
            (header[2] & 0xFF) == 0x4E &&
            (header[3] & 0xFF) == 0x47) {
            return true;
        }
        
        // GIF: 47 49 46 38 (GIF8)
        if (header.length >= 4 &&
            (header[0] & 0xFF) == 0x47 &&
            (header[1] & 0xFF) == 0x49 &&
            (header[2] & 0xFF) == 0x46 &&
            (header[3] & 0xFF) == 0x38) {
            return true;
        }
        
        // WebP: 52 49 46 46 (RIFF) + WebP 시그니처
        if (header.length >= 12 &&
            (header[0] & 0xFF) == 0x52 &&
            (header[1] & 0xFF) == 0x49 &&
            (header[2] & 0xFF) == 0x46 &&
            (header[3] & 0xFF) == 0x46 &&
            (header[8] & 0xFF) == 0x57 &&
            (header[9] & 0xFF) == 0x45 &&
            (header[10] & 0xFF) == 0x42 &&
            (header[11] & 0xFF) == 0x50) {
            return true;
        }
        
        return false;
    }

    /**
     * 의심스러운 패턴 검사
     */
    private boolean containsSuspiciousPatterns(byte[] data) {
        String content = new String(data).toLowerCase();
        
        // 스크립트 태그 검사
        if (content.contains("<script") || 
            content.contains("javascript:") ||
            content.contains("vbscript:") ||
            content.contains("onload=") ||
            content.contains("onerror=")) {
            return true;
        }
        
        // PHP 코드 검사
        if (content.contains("<?php") || content.contains("<?=")) {
            return true;
        }
        
        return false;
    }

    /**
     * 보안 파일 정보 DTO
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class SecureFileInfo {
        private String originalFilename;
        private String secureFilename;
        private String contentType;
        private long size;
        private String extension;
        private String tempPath;
        private String uploadType;
    }
}
```

---

## 🦠 VirusScanService 구현

### 📝 구현 코드
```java
package com.routepick.service.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.TimeUnit;

/**
 * 바이러스 스캔 서비스
 */
@Slf4j
@Service
public class VirusScanService {

    @Value("${security.virus-scan.enabled:false}")
    private boolean virusScanEnabled;

    @Value("${security.virus-scan.timeout:30}")
    private int scanTimeoutSeconds;

    /**
     * 파일 바이러스 스캔
     */
    public boolean scanFile(MultipartFile file) throws IOException {
        if (!virusScanEnabled) {
            log.debug("Virus scan disabled, skipping scan for file: {}", 
                     file.getOriginalFilename());
            return false;
        }

        log.info("Starting virus scan for file: {}", file.getOriginalFilename());
        
        try {
            // ClamAV 연동 (실제 환경에서는 ClamAV Java 클라이언트 사용)
            return performVirusScan(file.getInputStream());
            
        } catch (Exception e) {
            log.error("Virus scan failed for file: {}, error: {}", 
                     file.getOriginalFilename(), e.getMessage());
            // 스캔 실패 시 보안을 위해 악성파일로 간주
            return true;
        }
    }

    /**
     * 실제 바이러스 스캔 수행
     */
    private boolean performVirusScan(InputStream inputStream) {
        // TODO: ClamAV 또는 다른 바이러스 스캔 엔진 연동
        // 현재는 시뮬레이션 구현
        
        try {
            // 간단한 패턴 매칭으로 시뮬레이션
            byte[] buffer = new byte[1024];
            int bytesRead = inputStream.read(buffer);
            
            if (bytesRead > 0) {
                String content = new String(buffer, 0, bytesRead).toLowerCase();
                
                // 의심스러운 패턴 검사
                if (content.contains("eicar") || 
                    content.contains("x5o!p%@ap[4\\pzx54(p^)7cc)7}$eicar")) {
                    log.warn("EICAR test virus detected");
                    return true;
                }
            }
            
            return false;
            
        } catch (IOException e) {
            log.error("Error during virus scan: {}", e.getMessage());
            return true; // 에러 시 보수적으로 악성파일로 간주
        }
    }
}
```

---

## 🖼️ ImageProcessingService 구현

### 📝 구현 코드
```java
package com.routepick.service.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * 이미지 보안 처리 서비스
 */
@Slf4j
@Service
public class ImageProcessingService {

    /**
     * 이미지 재인코딩 (보안 강화)
     */
    public byte[] reencodeImage(MultipartFile file, String extension) throws IOException {
        BufferedImage originalImage;
        
        try (InputStream inputStream = file.getInputStream()) {
            originalImage = ImageIO.read(inputStream);
        }
        
        if (originalImage == null) {
            throw new IOException("이미지를 읽을 수 없습니다");
        }
        
        // 새로운 BufferedImage 생성 (EXIF 데이터 제거)
        BufferedImage cleanImage = new BufferedImage(
            originalImage.getWidth(),
            originalImage.getHeight(),
            BufferedImage.TYPE_INT_RGB
        );
        
        // 배경을 흰색으로 설정 (투명도 제거)
        Graphics2D g2d = cleanImage.createGraphics();
        g2d.setColor(Color.WHITE);
        g2d.fillRect(0, 0, cleanImage.getWidth(), cleanImage.getHeight());
        g2d.drawImage(originalImage, 0, 0, null);
        g2d.dispose();
        
        // 재인코딩
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        String formatName = getImageFormatName(extension);
        
        if (!ImageIO.write(cleanImage, formatName, outputStream)) {
            throw new IOException("이미지 재인코딩에 실패했습니다");
        }
        
        byte[] result = outputStream.toByteArray();
        log.debug("Image re-encoded: original size={}, new size={}", 
                 file.getSize(), result.length);
        
        return result;
    }

    /**
     * 확장자에서 ImageIO 포맷명 추출
     */
    private String getImageFormatName(String extension) {
        return switch (extension.toLowerCase()) {
            case "jpg", "jpeg" -> "jpeg";
            case "png" -> "png";
            case "gif" -> "gif";
            case "webp" -> "webp";
            default -> "jpeg"; // 기본값
        };
    }
}
```

---

## ⚠️ Exception 클래스들

### 📝 FileUploadSecurityException.java
```java
package com.routepick.exception.file;

import com.routepick.exception.BaseException;
import com.routepick.common.ErrorCode;

public class FileUploadSecurityException extends BaseException {
    public FileUploadSecurityException(String message) {
        super(ErrorCode.FILE_UPLOAD_SECURITY_ERROR, message);
    }
    
    public FileUploadSecurityException(String message, Throwable cause) {
        super(ErrorCode.FILE_UPLOAD_SECURITY_ERROR, message, cause);
    }
}
```

### 📝 InvalidFileFormatException.java
```java
package com.routepick.exception.file;

import com.routepick.exception.BaseException;
import com.routepick.common.ErrorCode;

public class InvalidFileFormatException extends BaseException {
    public InvalidFileFormatException(String message) {
        super(ErrorCode.INVALID_FILE_FORMAT, message);
    }
}
```

---

## 📋 구현 완료 사항
✅ **FileUploadSecurityService** - 종합적인 파일 보안 검증  
✅ **VirusScanService** - 바이러스 스캔 (ClamAV 연동 준비)  
✅ **ImageProcessingService** - 이미지 재인코딩, EXIF 제거  
✅ **보안 예외 클래스들** - 세분화된 에러 처리  

## 🔧 주요 보안 기능
- **Magic Number 검증** - 파일 확장자 스푸핑 방지
- **MIME 타입 검증** - Polyglot 파일 탐지
- **이미지 재인코딩** - EXIF 데이터 및 악성 코드 제거
- **파일명 보안** - 경로 탐색 공격 방지
- **크기 제한** - DoS 공격 방지
- **바이러스 스캔** - 악성 파일 차단

## ⚙️ 설정 파일 추가
```yaml
# application.yml
security:
  virus-scan:
    enabled: false  # 개발 환경에서는 false
    timeout: 30
    
file:
  upload:
    temp-dir: /tmp/routepick
    max-size: 10MB
    allowed-types: jpg,jpeg,png,gif,webp
```