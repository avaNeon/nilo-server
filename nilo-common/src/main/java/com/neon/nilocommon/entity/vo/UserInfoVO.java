package com.neon.nilocommon.entity.vo;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class UserInfoVO
{
    /**
     * 用户ID
     */
    private Long userId;

    private String nickName;

    private String avatar;

    private Integer gender;

    private String birthday;

    private String school;

    private String personalIntroduction;

    private Integer status;

    private String noticeInfo;

    private Integer theme;
}
