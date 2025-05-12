package com.routepick.common.dto;

import lombok.*;
import com.routepick.common.enums.SortOrder;
import com.routepick.common.enums.SortType;

import java.util.List;

/**
 * 페이지네이션을 위한 DTO 클래스
 * 요청(Request)과 응답(Response)을 포함하며, 정렬 관련 enum도 정의되어 있습니다.
 */
public class PageableDTO {

    /**
     * 페이지네이션 요청을 위한 내부 클래스
     * 페이지 번호, 페이지 크기, 정렬 순서, 정렬 타입을 포함합니다.
     */
    @Data
    public static class Request {
        /** 페이지 번호 (기본값: 1) */
        private int page = 1;
        /** 페이지당 항목 수 (기본값: 10) */
        private int limit = 10;
        /** 정렬 순서 (기본값: 오름차순) */     
        private SortOrder sortOrder = SortOrder.ASCENDING;
        /** 정렬 타입 (기본값: 최신순) */
        private SortType sortType = SortType.LATEST;
    }

    /**
     * 페이지네이션 응답을 위한 내부 클래스
     * 전체 항목 수, 페이지 크기, 현재 페이지 번호, 페이지 내용을 포함합니다.
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Response {
        /** 전체 항목 수 */
        private Long totalElements;
        /** 페이지 크기 */
        private Integer size;
        /** 현재 페이지 번호 */
        private Integer number;
        /** 페이지 내용 (제네릭 타입) */
        private List<?> content;
    }

  

 
}