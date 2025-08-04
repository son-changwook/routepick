package com.routepick.api.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 파일 보안 유틸리티 클래스
 * 파일 업로드 관련 보안 검증 기능을 제공합니다.
 */
@Slf4j
public class FileSecurityUtil {

    // 허용된 MIME 타입
    private static final Set<String> ALLOWED_MIME_TYPES = new HashSet<>(Arrays.asList(
        "image/jpeg",
        "image/jpg", 
        "image/png",
        "image/gif"
    ));
    
    // 위험한 파일명 패턴
    private static final Pattern DANGEROUS_FILENAME_PATTERN = Pattern.compile(
        ".*[<>:\"/\\\\|?*].*|.*\\.\\..*|.*\\.\\..*", Pattern.CASE_INSENSITIVE
    );
    
    // 악성 파일 시그니처 (일부 위험한 파일 형식)
    private static final byte[] EXE_SIGNATURE = {0x4D, 0x5A}; // MZ
    private static final byte[] ZIP_SIGNATURE = {0x50, 0x4B, 0x03, 0x04}; // PK
    private static final byte[] RAR_SIGNATURE = {0x52, 0x61, 0x72, 0x21}; // Rar!
    
    /**
     * 파일 보안 검증 (종합)
     * @param file 업로드된 파일
     * @return 검증 통과 여부
     * @throws SecurityException 보안 검증 실패 시
     */
    public static void validateFileSecurity(MultipartFile file) throws SecurityException {
        if (file == null) {
            throw new SecurityException("업로드할 파일이 없습니다.");
        }
        
        // 1. 기본 파일 검증
        validateBasicFile(file);
        
        // 2. MIME 타입 검증
        validateMimeType(file);
        
        // 3. 파일명 보안 검증
        validateFileName(file.getOriginalFilename());
        
        // 4. 악성 파일 시그니처 검증
        validateMaliciousSignature(file);
        
        // 5. 파일 크기 검증
        validateFileSize(file);
        
        log.info("파일 보안 검증 통과: {}", file.getOriginalFilename());
    }
    
    /**
     * 기본 파일 검증
     */
    private static void validateBasicFile(MultipartFile file) {
        if (file.isEmpty()) {
            throw new SecurityException("업로드할 파일이 비어있습니다.");
        }
        
        if (file.getSize() == 0) {
            throw new SecurityException("파일 크기가 0입니다.");
        }
    }
    
    /**
     * MIME 타입 검증
     */
    private static void validateMimeType(MultipartFile file) {
        String contentType = file.getContentType();
        if (contentType == null || contentType.isEmpty()) {
            throw new SecurityException("파일 타입을 확인할 수 없습니다.");
        }
        
        if (!ALLOWED_MIME_TYPES.contains(contentType.toLowerCase())) {
            throw new SecurityException("허용되지 않는 파일 타입입니다: " + contentType);
        }
    }
    
    /**
     * 파일명 보안 검증
     */
    private static void validateFileName(String filename) {
        if (filename == null || filename.isEmpty()) {
            throw new SecurityException("파일명이 없습니다.");
        }
        
        // 위험한 문자나 패턴 검사
        if (DANGEROUS_FILENAME_PATTERN.matcher(filename).matches()) {
            throw new SecurityException("위험한 파일명입니다: " + filename);
        }
        
        // 파일명 길이 제한
        if (filename.length() > 255) {
            throw new SecurityException("파일명이 너무 깁니다.");
        }
        
        // 경로 순회 공격 방지
        if (filename.contains("..") || filename.contains("/") || filename.contains("\\")) {
            throw new SecurityException("파일명에 경로 정보가 포함되어 있습니다.");
        }
        
        // 확장자 검증
        String extension = getFileExtension(filename);
        if (extension == null) {
            throw new SecurityException("파일 확장자가 없습니다.");
        }
        
        Set<String> allowedExtensions = new HashSet<>(Arrays.asList(".jpg", ".jpeg", ".png", ".gif"));
        if (!allowedExtensions.contains(extension.toLowerCase())) {
            throw new SecurityException("허용되지 않는 파일 확장자입니다: " + extension);
        }
    }
    
    /**
     * 악성 파일 시그니처 검증
     */
    private static void validateMaliciousSignature(MultipartFile file) {
        try (InputStream inputStream = file.getInputStream()) {
            byte[] header = new byte[8];
            int bytesRead = inputStream.read(header);
            
            if (bytesRead < 2) {
                return; // 너무 작은 파일은 건너뜀
            }
            
            // EXE 파일 검증
            if (isExecutableFile(header)) {
                throw new SecurityException("실행 파일은 업로드할 수 없습니다.");
            }
            
            // ZIP 파일 검증
            if (isZipFile(header)) {
                throw new SecurityException("압축 파일은 업로드할 수 없습니다.");
            }
            
            // RAR 파일 검증
            if (isRarFile(header)) {
                throw new SecurityException("압축 파일은 업로드할 수 없습니다.");
            }
            
        } catch (IOException e) {
            throw new SecurityException("파일 읽기에 실패했습니다.");
        }
    }
    
    /**
     * 실행 파일 시그니처 확인
     */
    private static boolean isExecutableFile(byte[] header) {
        return header.length >= 2 && 
               header[0] == EXE_SIGNATURE[0] && 
               header[1] == EXE_SIGNATURE[1];
    }
    
    /**
     * ZIP 파일 시그니처 확인
     */
    private static boolean isZipFile(byte[] header) {
        if (header.length < 4) return false;
        
        for (int i = 0; i < 4; i++) {
            if (header[i] != ZIP_SIGNATURE[i]) {
                return false;
            }
        }
        return true;
    }
    
    /**
     * RAR 파일 시그니처 확인
     */
    private static boolean isRarFile(byte[] header) {
        if (header.length < 4) return false;
        
        for (int i = 0; i < 4; i++) {
            if (header[i] != RAR_SIGNATURE[i]) {
                return false;
            }
        }
        return true;
    }
    
    /**
     * 파일 크기 검증
     */
    private static void validateFileSize(MultipartFile file) {
        long maxSize = 5 * 1024 * 1024; // 5MB
        
        if (file.getSize() > maxSize) {
            throw new SecurityException("파일 크기가 너무 큽니다. 최대 5MB까지 업로드 가능합니다.");
        }
    }
    
    /**
     * 안전한 파일 경로 생성
     */
    public static Path createSecurePath(String baseDir, String filename) {
        // 경로 정규화
        Path basePath = Paths.get(baseDir).toAbsolutePath().normalize();
        Path filePath = basePath.resolve(filename).normalize();
        
        // 경로 순회 공격 방지
        if (!filePath.startsWith(basePath)) {
            throw new SecurityException("경로 순회 공격이 감지되었습니다.");
        }
        
        return filePath;
    }
    
    /**
     * 파일 해시 계산
     */
    public static String calculateFileHash(byte[] fileBytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
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
    private static String getFileExtension(String filename) {
        int lastDotIndex = filename.lastIndexOf('.');
        if (lastDotIndex == -1) {
            return null;
        }
        return filename.substring(lastDotIndex);
    }
} 