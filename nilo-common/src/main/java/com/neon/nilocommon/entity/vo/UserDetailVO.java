package com.neon.nilocommon.entity.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class UserDetailVO
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

    private Integer currentCoin;

    private Integer followerCount;

    private Integer followingCount;

    private Boolean hasFollowed;
}
