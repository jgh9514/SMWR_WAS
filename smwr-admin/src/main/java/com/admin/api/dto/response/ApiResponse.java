package com.admin.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "API 응답")
public class ApiResponse {
    
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
    
    @Schema(description = "생성자 ID", example = "admin")
    private String crtUserId;
    
    @Schema(description = "생성일시", example = "2024-01-01 12:00:00")
    private String crtDate;
    
    @Schema(description = "수정자 ID", example = "admin")
    private String uptUserId;
    
    @Schema(description = "수정일시", example = "2024-01-02 13:00:00")
    private String uptDate;
}

