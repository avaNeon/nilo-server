package com.neon.nilocommon.entity.dto;

import com.neon.nilocommon.entity.constants.Constants;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class RegisterUserInfoDTO
{
    @NotBlank(message = "email不能为空")
    @Email(message = "email不符合格式")
    @Size(max = 150, message = "email长度过大")
    String email;

    @NotBlank(message = "nickName不能为空")
    @Size(max = 20, message = "nickName长度过大")
    String nickName;

    @Pattern(regexp = Constants.PASSWORD_REGEXP, message = "密码格式不合法")
    String password;

    @NotBlank
    String captchaKey;

    @NotBlank
    String code;
}
