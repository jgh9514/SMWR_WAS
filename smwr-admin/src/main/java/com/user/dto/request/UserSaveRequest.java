package com.admin.user.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

@Data
@Schema(description = "사용자 저장 요청")
public class UserSaveRequest {
    
    @Schema(description = "사용자 ID (수정 시 필수)", example = "user001")
    private String userId;
    
    @NotBlank(message = "사용자 이름은 필수입니다")
    @Size(max = 100, message = "사용자 이름은 100자 이하여야 합니다")
    @Schema(description = "사용자 이름", example = "홍길동", required = true)
    private String userNm;
    
    @Schema(description = "비밀번호 (생성 시)", example = "password123")
    private String userPw;
    
    @Schema(description = "사용 여부", example = "Y")
    private String usgYn;
    
    @Schema(description = "언어 코드", example = "KO")
    private String langCd;
    
    @Schema(description = "디바이스 ID", example = "device123")
    private String dvcId;
    
    @Schema(description = "역할 ID", example = "ROLE_USER")
    private String roleId;
}

