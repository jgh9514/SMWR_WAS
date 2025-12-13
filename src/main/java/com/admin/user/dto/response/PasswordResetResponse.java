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
@Schema(description = "비밀번호 재설정 응답")
public class PasswordResetResponse {
    
    @Schema(description = "처리 결과", example = "SUCCESS", allowableValues = {"SUCCESS", "PWDNOTMATCHED", "FAIL"})
    private String result;
    
    @Schema(description = "메시지", example = "비밀번호가 성공적으로 변경되었습니다.")
    private String message;
}

