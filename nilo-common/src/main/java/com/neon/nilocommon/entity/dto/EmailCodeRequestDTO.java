package com.neon.nilocommon.entity.dto;

import com.neon.nilocommon.entity.enums.EmailScene;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 申请邮箱验证码请求
 */
@Getter
@Setter
@NoArgsConstructor
public class EmailCodeRequestDTO
{
    @NotBlank(message = "email不能为空")
    @Email(message = "email不符合格式")
    @Size(max = 150, message = "email长度过大")
    String email;

    /**
     * 场景<hr/>
     * <p>REGISTER：注册；RESET_PASSWORD：找回密码（若邮箱未注册，会自动转为REGISTER场景）</p>
     */
    @NotNull(message = "scene不能为空")
    EmailScene scene;
}
