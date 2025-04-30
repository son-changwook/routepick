package com.scw.routepick.domain;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class ClimbingLevel {
    private Long levelId; // 레벨 ID
    private String levelName; // 레벨 이름
}