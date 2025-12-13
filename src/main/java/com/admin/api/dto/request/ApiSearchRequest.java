package com.admin.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "API 검색 요청")
public class ApiSearchRequest {
    
    @Schema(description = "API ID", example = "API001")
    private String apiId;
    
    @Schema(description = "업무 코드", example = "USER")
    private String bsnsCd;
    
    @Schema(description = "상세 업무 코드", example = "LIST")
    private String dtlBsnsCd;
    
    @Schema(description = "API 설명", example = "사용자 목록 조회")
    private String apiTxt;
    
    @Schema(description = "API URL", example = "/api/v1/user/list")
    private String apiUrl;
}

