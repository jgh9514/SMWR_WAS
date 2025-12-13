package com.admin.user.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

@Data
@Schema(description = "비밀번호 재설정 요청")
public class PasswordResetRequest {
    
    @NotBlank(message = "사용자 ID는 필수입니다")
    @Schema(description = "사용자 ID", example = "user001", required = true)
    private String userId;
    
    @NotBlank(message = "새 비밀번호는 필수입니다")
    @Size(min = 8, max = 20, message = "비밀번호는 8-20자 사이여야 합니다")
    @Schema(description = "새 비밀번호", example = "newPassword123", required = true)
    private String newPassword;
    
    @NotBlank(message = "비밀번호 확인은 필수입니다")
    @Schema(description = "비밀번호 확인", example = "newPassword123", required = true)
    private String confirmPassword;
}

