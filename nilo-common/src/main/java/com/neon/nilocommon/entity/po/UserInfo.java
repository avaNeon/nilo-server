package com.neon.nilocommon.entity.po;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.Date;

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

    @NotBlank(message = "password不能为空")
    private String password;

    private Integer gender;

    private String birthday;

    private String school;

    private String personalIntroduction;

    @NotNull(message = "registerTime不能为空")
    private Date registerTime;

    private Date lastLoginTime;

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