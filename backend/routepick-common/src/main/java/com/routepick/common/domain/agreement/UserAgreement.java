package com.routepick.common.domain.agreement;

import com.routepick.common.domain.common.BaseDomain;
import com.routepick.common.enums.AgreementType;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * 사용자 약관 동의 도메인 클래스
 */
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
public class UserAgreement extends BaseDomain {
    
    private Integer userAgreementId;
    private Integer userId;
    private Integer agreementContentId;
    private AgreementType agreementType;
    private String version;
    private Boolean isAgreed;
    private String ipAddress;
    private String userAgent;
} 