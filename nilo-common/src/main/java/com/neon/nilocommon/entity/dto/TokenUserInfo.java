package com.neon.nilocommon.entity.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.neon.nilocommon.entity.vo.BriefUserInfoVO;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@JsonIgnoreProperties(ignoreUnknown = true) // 反序列化时出现没有的字段直接忽略，防止报错
public class TokenUserInfo
{
    BriefUserInfoVO userInfo;

    /**
     * 在这个时间失效
     */
    private Long expireTime;

    private String token;

    private Integer followerCount;

    private Integer followingCount;

    private Integer currentCoin;
}
