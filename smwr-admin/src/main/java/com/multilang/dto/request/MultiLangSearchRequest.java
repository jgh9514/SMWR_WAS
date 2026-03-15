package com.admin.multilang.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "다국어 검색 요청")
public class MultiLangSearchRequest {
    
    @Schema(description = "다국어 타입 코드", example = "MN")
    private String mlangTpCd;
    
    @Schema(description = "다국어 코드", example = "M01")
    private String mlangCd;
    
    @Schema(description = "언어 코드", example = "KO")
    private String langCd;
    
    @Schema(description = "다국어 텍스트", example = "메뉴1")
    private String mlangTxt;
}

