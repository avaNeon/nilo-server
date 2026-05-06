package com.neon.nilocommon.entity.po;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 用户统计信息PO
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UserState
{
    private Integer followerCount;

    private Integer followingCount;

    private Integer currentCoin;
}
