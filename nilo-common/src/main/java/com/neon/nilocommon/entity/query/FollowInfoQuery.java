package com.neon.nilocommon.entity.query;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class FollowInfoQuery extends BaseQuery
{
    /**
     * 关注者用户ID
     */
    private Long followerUserId;

    /**
     * 被关注人用户ID
     */
    private Long followingUserId;

    /**
     *
     */
    private String followTime;

    private String followTimeStart;

    private String followTimeEnd;
}
