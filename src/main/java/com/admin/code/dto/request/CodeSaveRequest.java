package com.admin.code.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@Schema(description = "코드 저장 요청 (통합)")
public class CodeSaveRequest {
    
    @Schema(description = "코드 그룹 번호", example = "CO00000001")
    private String cdGrpNo;
    
    @Schema(description = "추가할 코드 그룹 목록")
    private List<Map<String, Object>> insertGrpRow;
    
    @Schema(description = "수정할 코드 그룹 목록")
    private List<Map<String, Object>> updateGrpRow;
    
    @Schema(description = "삭제할 코드 그룹 목록")
    private List<Map<String, Object>> deleteGrpRow;
    
    @Schema(description = "추가할 코드 목록")
    private List<Map<String, Object>> insertRow;
    
    @Schema(description = "수정할 코드 목록")
    private List<Map<String, Object>> updateRow;
    
    @Schema(description = "삭제할 코드 목록")
    private List<Map<String, Object>> deleteRow;
}

