package com.neon.nilocommon.entity.dto;

import com.neon.nilocommon.entity.constants.Constants;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class LoginAdminDTO
{
    @NotBlank
    String account;

    @Pattern(regexp = Constants.PASSWORD_REGEXP, message = "密码格式不合法")
    String password;

    @NotBlank
    String captchaKey;

    @NotBlank
    String code;
}
