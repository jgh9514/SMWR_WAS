package com.admin.code.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "코드 그룹 검색 요청")
public class CodeGroupSearchRequest {
    
    @Schema(description = "코드 그룹 번호", example = "CO00000001")
    private String cdGrpNo;
    
    @Schema(description = "코드 그룹명", example = "사용자구분")
    private String cdGrpNm;
    
    @Schema(description = "사용 여부", example = "Y")
    private String usgYn;
}

