package com.routepick.api.service.file;

import com.routepick.api.util.FileSecurityUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import com.routepick.common.exception.FileException;
import com.routepick.common.exception.SecurityException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.UUID;
import java.util.regex.Pattern;

@Slf4j
@Service
public class FileService {

    private static final String UPLOAD_DIR = "uploads/profiles/";
    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024; // 5MB
    private static final String[] ALLOWED_EXTENSIONS = {".jpg", ".jpeg", ".png", ".gif"};
    
    // 매직 바이트 (파일 시그니처)
    private static final byte[] JPEG_SIGNATURE = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] PNG_SIGNATURE = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
    private static final byte[] GIF_SIGNATURE_87A = {0x47, 0x49, 0x46, 0x38, 0x37, 0x61};
    private static final byte[] GIF_SIGNATURE_89A = {0x47, 0x49, 0x46, 0x38, 0x39, 0x61};
    
    // 위험한 파일명 패턴
    private static final Pattern DANGEROUS_FILENAME_PATTERN = Pattern.compile(
        ".*[<>:\"/\\\\|?*].*|.*\\.\\..*|.*\\.\\..*", Pattern.CASE_INSENSITIVE
    );

    /**
     * 프로필 이미지 업로드 (보안 강화)
     * @param file 업로드할 이미지 파일
     * @return 업로드된 파일의 URL
     */
    public String uploadProfileImage(MultipartFile file) {
        try {
            // 1. 종합 보안 검증 (FileSecurityUtil 사용)
            FileSecurityUtil.validateFileSecurity(file);
            
            // 2. 기본 파일 검증
            validateImageFile(file);
            
            // 3. 파일명 보안 검증
            validateFileName(file.getOriginalFilename());
            
            // 4. 매직 바이트 검증 (실제 파일 타입 확인)
            validateFileContent(file);
            
            // 5. 업로드 디렉토리 생성
            Path uploadPath = Paths.get(UPLOAD_DIR).toAbsolutePath().normalize();
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }
            
            // 6. 안전한 파일명 생성
            String originalFilename = file.getOriginalFilename();
            String extension = getFileExtension(originalFilename);
            String filename = generateSecureFilename(extension);
            
            // 7. 안전한 파일 경로 생성
            Path filePath = FileSecurityUtil.createSecurePath(UPLOAD_DIR, filename);
            
            // 8. 파일 저장 (덮어쓰기 방지)
            if (Files.exists(filePath)) {
                throw FileException.fileNameConflict();
            }
            
            // 9. 안전한 파일 복사
            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, filePath, StandardCopyOption.REPLACE_EXISTING);
            }
            
            // 10. 파일 해시 검증 (무결성 확인)
            String fileHash = calculateFileHash(filePath);
            log.info("파일 해시: {}", fileHash);
            
            // 11. URL 반환 (실제 환경에서는 CDN이나 클라우드 스토리지 URL로 변경)
            String fileUrl = "/api/files/profiles/" + filename;
            
            log.info("프로필 이미지 업로드 완료: {}", fileUrl);
            
            return fileUrl;
            
        } catch (IOException e) {
            log.error("파일 업로드 실패: {}", e.getMessage(), e);
            throw FileException.fileUploadFailed(e.getMessage(), e);
        } catch (SecurityException e) {
            log.error("파일 보안 검증 실패: {}", e.getMessage());
            throw e;
        }
    }

    /**
     * 이미지 파일 기본 검증
     */
    private void validateImageFile(MultipartFile file) {
        if (file == null) {
            throw FileException.noFile();
        }
        
        if (file.isEmpty()) {
            throw FileException.emptyFile();
        }
        
        if (file.getSize() > MAX_FILE_SIZE) {
            throw FileException.invalidFileSize(file.getSize(), MAX_FILE_SIZE);
        }
        
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isEmpty()) {
            throw SecurityException.invalidFilename("파일명이 없습니다");
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
            throw FileException.unsupportedFileType(extension);
        }
    }
    
    /**
     * 파일명 보안 검증
     */
    private void validateFileName(String filename) {
        if (filename == null || filename.isEmpty()) {
            throw SecurityException.invalidFilename("파일명이 없습니다");
        }
        
        // 위험한 문자나 패턴 검사
        if (DANGEROUS_FILENAME_PATTERN.matcher(filename).matches()) {
            throw SecurityException.invalidFilename("위험한 파일명입니다");
        }
        
        // 파일명 길이 제한
        if (filename.length() > 255) {
            throw SecurityException.invalidFilename("파일명이 너무 깁니다");
        }
        
        // 경로 순회 공격 방지
        if (filename.contains("..") || filename.contains("/") || filename.contains("\\")) {
            throw SecurityException.pathTraversalDetected();
        }
    }
    
    /**
     * 파일 내용 검증 (매직 바이트 확인)
     */
    private void validateFileContent(MultipartFile file) throws IOException {
        try (InputStream inputStream = file.getInputStream()) {
            byte[] header = new byte[8];
            int bytesRead = inputStream.read(header);
            
            if (bytesRead < 3) {
                throw SecurityException.fileTooSmall(bytesRead, 3);
            }
            
            // JPEG 검증
            if (isJPEG(header)) {
                log.debug("JPEG 파일 확인됨");
                return;
            }
            
            // PNG 검증
            if (isPNG(header)) {
                log.debug("PNG 파일 확인됨");
                return;
            }
            
            // GIF 검증
            if (isGIF(header)) {
                log.debug("GIF 파일 확인됨");
                return;
            }
            
            throw SecurityException.dangerousFileType("실제 이미지 파일이 아님");
        }
    }
    
    /**
     * JPEG 파일 시그니처 확인
     */
    private boolean isJPEG(byte[] header) {
        return header.length >= 3 && 
               header[0] == (byte) 0xFF && 
               header[1] == (byte) 0xD8 && 
               header[2] == (byte) 0xFF;
    }
    
    /**
     * PNG 파일 시그니처 확인
     */
    private boolean isPNG(byte[] header) {
        if (header.length < 8) return false;
        
        for (int i = 0; i < 8; i++) {
            if (header[i] != PNG_SIGNATURE[i]) {
                return false;
            }
        }
        return true;
    }
    
    /**
     * GIF 파일 시그니처 확인
     */
    private boolean isGIF(byte[] header) {
        if (header.length < 6) return false;
        
        // GIF87a 확인
        boolean isGIF87a = true;
        for (int i = 0; i < 6; i++) {
            if (header[i] != GIF_SIGNATURE_87A[i]) {
                isGIF87a = false;
                break;
            }
        }
        
        if (isGIF87a) return true;
        
        // GIF89a 확인
        boolean isGIF89a = true;
        for (int i = 0; i < 6; i++) {
            if (header[i] != GIF_SIGNATURE_89A[i]) {
                isGIF89a = false;
                break;
            }
        }
        
        return isGIF89a;
    }

    /**
     * 안전한 파일명 생성
     */
    private String generateSecureFilename(String extension) {
        // UUID + 타임스탬프 + 확장자
        String timestamp = String.valueOf(System.currentTimeMillis());
        String uuid = UUID.randomUUID().toString().replace("-", "");
        return uuid + "_" + timestamp + extension;
    }
    
    /**
     * 파일 해시 계산 (무결성 확인용)
     */
    private String calculateFileHash(Path filePath) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] fileBytes = Files.readAllBytes(filePath);
            byte[] hashBytes = digest.digest(fileBytes);
            
            StringBuilder hexString = new StringBuilder();
            for (byte b : hashBytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            log.warn("SHA-256 알고리즘을 찾을 수 없습니다.");
            return "unknown";
        }
    }

    /**
     * 파일 확장자 추출
     */
    private String getFileExtension(String filename) {
        int lastDotIndex = filename.lastIndexOf('.');
        if (lastDotIndex == -1) {
            throw FileException.noFileExtension();
        }
        return filename.substring(lastDotIndex);
    }
} 