package com.admin.role.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@Schema(description = "역할 저장 요청")
public class RoleSaveRequest {
    
    @Schema(description = "추가할 역할 목록")
    private List<Map<String, Object>> insertRow;
    
    @Schema(description = "수정할 역할 목록")
    private List<Map<String, Object>> updateRow;
    
    @Schema(description = "삭제할 역할 목록")
    private List<Map<String, Object>> deleteRow;
}

