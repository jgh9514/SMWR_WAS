package com.admin.multilang.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@Schema(description = "다국어 저장 요청")
public class MultiLangSaveRequest {
    
    @Schema(description = "추가할 다국어 목록")
    private List<Map<String, Object>> insertRow;
    
    @Schema(description = "수정할 다국어 목록")
    private List<Map<String, Object>> updateRow;
    
    @Schema(description = "삭제할 다국어 목록")
    private List<Map<String, Object>> deleteRow;
}

