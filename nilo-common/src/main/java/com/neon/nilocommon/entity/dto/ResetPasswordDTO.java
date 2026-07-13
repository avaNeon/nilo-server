package com.neon.nilocommon.entity.dto;

import com.neon.nilocommon.entity.constants.Constants;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 通过邮箱验证码重置密码请求
 */
@Getter
@Setter
@NoArgsConstructor
public class ResetPasswordDTO
{
    @NotBlank(message = "email不能为空")
    @Email(message = "email不符合格式")
    @Size(max = 150, message = "email长度过大")
    String email;

    @NotBlank(message = "emailCode不能为空")
    @Pattern(regexp = Constants.EMAIL_CODE_REGEXP, message = "emailCode格式不合法")
    String emailCode;

    @Pattern(regexp = Constants.PASSWORD_REGEXP, message = "密码格式不合法")
    String newPassword;
}
