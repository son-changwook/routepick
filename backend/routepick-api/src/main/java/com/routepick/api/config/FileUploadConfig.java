package com.routepick.api.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 파일 업로드 설정 클래스
 */
@Data
@Component
@ConfigurationProperties(prefix = "file-upload")
public class FileUploadConfig {
    
    private long maxSize = 5242880; // 5MB 기본값
    private String allowedExtensions = ".jpg,.jpeg,.png,.gif";
    
    /**
     * 허용된 파일 확장자 배열을 반환합니다.
     */
    public String[] getAllowedExtensionsArray() {
        return allowedExtensions.split(",");
    }
    
    /**
     * 파일 크기가 허용된 최대 크기보다 작은지 확인합니다.
     */
    public boolean isFileSizeValid(long fileSize) {
        return fileSize <= maxSize;
    }
    
    /**
     * 파일 확장자가 허용된 확장자인지 확인합니다.
     */
    public boolean isExtensionAllowed(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return false;
        }
        
        String extension = fileName.substring(fileName.lastIndexOf(".")).toLowerCase();
        for (String allowedExt : getAllowedExtensionsArray()) {
            if (allowedExt.trim().toLowerCase().equals(extension)) {
                return true;
            }
        }
        return false;
    }
} 