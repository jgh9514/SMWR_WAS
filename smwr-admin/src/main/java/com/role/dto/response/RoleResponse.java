package com.admin.role.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "역할 응답")
public class RoleResponse {
    
    @Schema(description = "역할 ID", example = "ROLE_ADMIN")
    private String roleId;
    
    @Schema(description = "역할명", example = "관리자")
    private String roleNm;
    
    @Schema(description = "사용 여부", example = "Y")
    private String usgYn;
    
    @Schema(description = "삭제 여부", example = "N")
    private String delYn;
    
    @Schema(description = "생성일시", example = "2024-01-01 12:00:00")
    private String crtDate;
}

