package com.neon.nilocommon.entity.po;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NotNull(message = "请求参数包装类不能为空")
public class UserInfo
{
    @NotBlank(message = "userId不能为空")
    private String userId;

    @NotBlank(message = "nickName不能为空")
    private String nickName;

    @NotBlank(message = "email不能为空")
    private String email;

    /**
     * 密码加密后固定60位
     */
    @NotBlank(message = "password不能为空")
    private String password;

    private Integer gender;

    private String birthday;

    private String school;

    private String personalIntroduction;

    @NotNull(message = "registerTime不能为空")
    @Pattern(regexp = "^\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}$", message = "时间格式必须为 yyyy-MM-dd HH:mm:ss")
    private LocalDateTime registerTime;

    @Pattern(regexp = "^\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}$", message = "时间格式必须为 yyyy-MM-dd HH:mm:ss")
    private LocalDateTime lastLoginTime;

    private String lastLoginIp;

    @NotNull(message = "status不能为空")
    private Integer status;

    private String noticeInfo;

    @NotNull(message = "totalCoin不能为空")
    private Integer totalCoin;

    @NotNull(message = "currentCoin不能为空")
    private Integer currentCoin;

    @NotNull(message = "theme不能为空")
    private Integer theme;
}