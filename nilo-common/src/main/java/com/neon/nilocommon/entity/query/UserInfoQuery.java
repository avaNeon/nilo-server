package com.neon.nilocommon.entity.query;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * {@link com.neon.nilocommon.entity.po.UserInfo}的查询类，可以实现属性模糊搜索的功能
 */
@EqualsAndHashCode(callSuper = false) // 不要比较父类的属性
@Data
public class UserInfoQuery extends BaseQuery
{
    /**
     * 用户id
     */
    private String userId;

    private String userIdFuzzy;

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
    private String registerTime;

    private String registerTimeStart;

    private String registerTimeEnd;

    /**
     * 最后登录时间
     */
    private String lastLoginTime;

    private String lastLoginTimeStart;

    private String lastLoginTimeEnd;

    /**
     * 最后登录IP
     */
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
