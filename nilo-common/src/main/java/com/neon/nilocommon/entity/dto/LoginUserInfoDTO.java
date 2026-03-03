package com.neon.nilocommon.entity.dto;

import com.neon.nilocommon.entity.constants.Constants;
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
public class LoginUserInfoDTO
{
    @Email(message = "email不符合格式")
    @Size(max = 150, message = "email长度过大")
    String email;

    @Pattern(regexp = Constants.PASSWORD_REGEXP, message = "密码格式不合法")
    String password;

    @NotBlank
    String captchaKey;

    @NotBlank
    String code;
}
