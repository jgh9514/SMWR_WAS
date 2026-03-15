package com.admin.multilang.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "다국어 응답")
public class MultiLangResponse {
    
    @Schema(description = "다국어 타입 코드", example = "MN")
    private String mlangTpCd;
    
    @Schema(description = "다국어 코드", example = "M01")
    private String mlangCd;
    
    @Schema(description = "언어 코드", example = "KO")
    private String langCd;
    
    @Schema(description = "다국어 텍스트", example = "메뉴1")
    private String mlangTxt;
    
    @Schema(description = "사용 여부", example = "Y")
    private String usgYn;
    
    @Schema(description = "삭제 여부", example = "N")
    private String delYn;
}

