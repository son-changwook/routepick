package com.routepick.common.domain.agreement;

import com.routepick.common.domain.common.BaseDomain;
import com.routepick.common.enums.AgreementType;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.time.LocalDate;

/**
 * 약관 내용 도메인 클래스
 */
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
public class AgreementContent extends BaseDomain {
    
    private Integer agreementContentId;
    private AgreementType agreementType;
    private String version;
    private String title;
    private String content;
    private Boolean isRequired;
    private LocalDate effectiveDate;
    private Integer createdBy;
    private Boolean isActive;
} 