package com.neon.nilocommon.entity.po;

import lombok.*;

import java.time.LocalDateTime;


@ToString
@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class FollowInfo
{
    /**
     * 关注者用户ID
     */
    private Long followerUserId;
    /**
     * 被关注人用户ID
     */
    private Long followingUserId;

    private LocalDateTime followTime;
}
