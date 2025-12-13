package com.admin.user.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "사용자 응답")
public class UserResponse {
    
    @Schema(description = "사용자 ID", example = "user001")
    private String userId;
    
    @Schema(description = "사용자 이름", example = "홍길동")
    private String userNm;
    
    @Schema(description = "사용 여부", example = "Y")
    private String usgYn;
    
    @Schema(description = "삭제 여부", example = "N")
    private String delYn;
    
    @Schema(description = "언어 코드", example = "KO")
    private String langCd;
    
    @Schema(description = "생성일시", example = "2024-01-01 12:00:00")
    private String crtDate;
    
    @Schema(description = "수정일시", example = "2024-01-02 13:00:00")
    private String uptDate;
}

