package com.neon.nilocommon.entity.po;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDateTime;

@Getter
@Setter
@ToString
public class UserInfo
{
    private Long userId;

    private String nickName;

    private String email;

    private String password;

    private Integer gender;

    private String birthday;

    private String school;

    private String personalIntroduction;

    private LocalDateTime registerTime;

    private LocalDateTime lastLoginTime;

    private String lastLoginIp;

    private Integer status;

    private String noticeInfo;

    private Integer totalCoin;

    private Integer currentCoin;

    private Integer theme;
}