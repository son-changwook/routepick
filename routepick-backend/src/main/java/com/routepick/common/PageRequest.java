package com.routepick.common;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "페이지 요청 정보")
public class PageRequest {

    @Schema(description = "페이지 번호 (0부터 시작)", example = "0", defaultValue = "0")
    private int page = 0;

    @Schema(description = "페이지 크기", example = "20", defaultValue = "20")
    private int size = Constants.DEFAULT_PAGE_SIZE;

    @Schema(description = "정렬 기준", example = "createdAt,desc")
    private String sort;

    public org.springframework.data.domain.PageRequest toPageable() {
        if (size > Constants.MAX_PAGE_SIZE) {
            size = Constants.MAX_PAGE_SIZE;
        }
        
        if (sort == null || sort.isEmpty()) {
            return org.springframework.data.domain.PageRequest.of(page, size);
        }

        String[] sortParams = sort.split(",");
        String property = sortParams[0];
        org.springframework.data.domain.Sort.Direction direction = 
            sortParams.length > 1 && "desc".equalsIgnoreCase(sortParams[1]) 
                ? org.springframework.data.domain.Sort.Direction.DESC 
                : org.springframework.data.domain.Sort.Direction.ASC;

        return org.springframework.data.domain.PageRequest.of(
            page, 
            size, 
            org.springframework.data.domain.Sort.by(direction, property)
        );
    }
}