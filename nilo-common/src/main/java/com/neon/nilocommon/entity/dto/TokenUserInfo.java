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

    // 下面三个数字在关注、投币时会发生改变，我们需要根据情况更新这个值
    private Integer followerCount;

    private Integer followingCount;

    private Integer currentCoin;
}
