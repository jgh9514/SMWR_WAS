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
@Schema(description = "코드 응답")
public class CodeResponse {
    
    @Schema(description = "코드 그룹 번호", example = "CO00000001")
    private String cdGrpNo;
    
    @Schema(description = "코드", example = "01")
    private String cd;
    
    @Schema(description = "코드명", example = "관리자")
    private String cdNm;
    
    @Schema(description = "정렬순서", example = "1")
    private Long sortSn;
    
    @Schema(description = "메모1", example = "관리자 권한")
    private String memo1;
    
    @Schema(description = "메모2", example = "")
    private String memo2;
    
    @Schema(description = "메모3", example = "")
    private String memo3;
    
    @Schema(description = "사용 여부", example = "Y")
    private String usgYn;
    
    @Schema(description = "삭제 여부", example = "N")
    private String delYn;
}

