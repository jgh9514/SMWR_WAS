package com.admin.user.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "사용자 검색 요청")
public class UserSearchRequest {
    
    @Schema(description = "사용자 ID", example = "user001")
    private String userId;
    
    @Schema(description = "사용자 이름", example = "홍길동")
    private String userNm;
    
    @Schema(description = "사용 여부", example = "Y")
    private String usgYn;
    
    @Schema(description = "언어 코드", example = "KO")
    private String langCd;
}

