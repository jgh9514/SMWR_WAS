package com.admin.code.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotBlank;

@Data
@Schema(description = "코드 검색 요청")
public class CodeSearchRequest {
    
    @NotBlank(message = "코드 그룹 번호는 필수입니다")
    @Schema(description = "코드 그룹 번호", example = "CO00000001", required = true)
    private String cdGrpNo;
    
    @Schema(description = "코드", example = "01")
    private String cd;
    
    @Schema(description = "코드명", example = "관리자")
    private String cdNm;
    
    @Schema(description = "사용 여부", example = "Y")
    private String usgYn;
}

