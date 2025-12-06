package com.neon.nilocommon.entity.po;

import lombok.Getter;
import lombok.Setter;

import java.util.Date;

@Getter
@Setter
public class UserInfo
{
    private String userId;

    private String nickName;

    private String email;

    private String password;

    private Integer gender;

    private String birthday;

    private String school;

    private String personalIntroduction;

    private Date registerTime;

    private Date lastLoginTime;

    private String lastLoginIp;

    private Integer status;

    private String noticeInfo;

    private Integer totalCoin;

    private Integer currentCoin;

    private Integer theme;
}