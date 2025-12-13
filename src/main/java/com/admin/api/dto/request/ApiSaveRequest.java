package com.admin.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@Schema(description = "API 저장 요청")
public class ApiSaveRequest {
    
    @Schema(description = "추가할 API 목록")
    private List<Map<String, Object>> insertRow;
    
    @Schema(description = "수정할 API 목록")
    private List<Map<String, Object>> updateRow;
    
    @Schema(description = "삭제할 API 목록")
    private List<Map<String, Object>> deleteRow;
    
    @Schema(description = "역할 매핑 데이터")
    private Map<String, Object> chkData;
}

