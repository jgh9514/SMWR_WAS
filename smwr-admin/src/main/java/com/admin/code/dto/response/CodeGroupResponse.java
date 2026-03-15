package com.admin.code.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "코드 그룹 응답")
public class CodeGroupResponse {
    
    @Schema(description = "코드 그룹 번호", example = "CO00000001")
    private String cdGrpNo;
    
    @Schema(description = "코드 그룹명", example = "사용자구분")
    private String cdGrpNm;
    
    @Schema(description = "정렬순서", example = "1")
    private Long sortSn;
    
    @Schema(description = "사용 여부", example = "Y")
    private String usgYn;
    
    @Schema(description = "삭제 여부", example = "N")
    private String delYn;
    
    @Schema(description = "생성일시", example = "2024-01-01 12:00:00")
    private String crtDate;
    
    @Schema(description = "수정일시", example = "2024-01-02 13:00:00")
    private String uptDate;
}

