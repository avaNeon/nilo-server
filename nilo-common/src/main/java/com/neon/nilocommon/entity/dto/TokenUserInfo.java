package com.neon.nilocommon.entity.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@JsonIgnoreProperties(ignoreUnknown = true) // 反序列化时出现没有的字段直接忽略，防止报错
public class TokenUserInfo
{
    private String userId;

    private String nickName;

    private String avatar;

    /**
     * 在这个时间失效
     */
    private Long expireTime;

    private String token;

    private Integer followerCount;

    private Integer currentCoin;

    private Integer followingCount;
}
