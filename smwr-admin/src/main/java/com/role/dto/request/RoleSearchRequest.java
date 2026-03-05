package com.admin.role.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "역할 검색 요청")
public class RoleSearchRequest {
    
    @Schema(description = "역할 ID", example = "ROLE_ADMIN")
    private String roleId;
    
    @Schema(description = "역할명", example = "관리자")
    private String roleNm;
    
    @Schema(description = "사용 여부", example = "Y")
    private String usgYn;
}

