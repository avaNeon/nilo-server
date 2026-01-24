package com.neon.nilocommon.entity.query;


import com.neon.nilocommon.entity.annotation.BlankRestriction;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * {@link com.neon.nilocommon.entity.po.UserInfo}的查询类，可以实现属性模糊搜索的功能
 */
@Getter
@Setter
@NotNull
@BlankRestriction
public class UserInfoQuery extends BaseQuery
{
    /**
     * 用户id
     */
    private Long userId;

    /**
     * 昵称
     */
    private String nickName;

    private String nickNameFuzzy;

    /**
     * 邮箱
     */
    private String email;

    private String emailFuzzy;

    /**
     * 密码
     */
    private String password;

    private String passwordFuzzy;

    /**
     * 0:女 1:男 2:未知
     */
    private Integer gender;

    /**
     * 出生日期
     */
    private String birthday;

    private String birthdayFuzzy;

    /**
     * 学校
     */
    private String school;

    private String schoolFuzzy;

    /**
     * 个人简介
     */
    private String personalIntroduction;

    private String personalIntroductionFuzzy;

    /**
     * 加入时间
     */
    private LocalDateTime registerTime;
    private LocalDate registerTimeStart;
    private LocalDate registerTimeEnd;

    /**
     * 最后登录时间
     */
    private LocalDateTime lastLoginTime;
    private LocalDate lastLoginTimeStart;
    private LocalDate lastLoginTimeEnd;


    private String lastLoginIp;
    private String lastLoginIpFuzzy;

    /**
     * 0:禁用 1:正常
     */
    private Integer status;

    /**
     * 空间公告
     */
    private String noticeInfo;

    private String noticeInfoFuzzy;

    /**
     * 硬币总数
     */
    private Integer totalCoin;

    /**
     * 当前硬币数
     */
    private Integer currentCoin;

    /**
     * 主题
     */
    private Integer theme;


}
