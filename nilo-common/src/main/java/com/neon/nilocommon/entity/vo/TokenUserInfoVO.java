package com.neon.nilocommon.entity.vo;

import com.neon.nilocommon.entity.vo.userInfo.BriefUserInfoVO;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TokenUserInfoVO
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
