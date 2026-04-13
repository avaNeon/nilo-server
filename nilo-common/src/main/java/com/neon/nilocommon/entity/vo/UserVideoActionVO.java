package com.neon.nilocommon.entity.vo;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class UserVideoActionVO
{
    /**
     * 1:视频点赞 2:视频收藏 3:视频投币
     */
    private Integer actionType;

    /**
     * 投币数量
     */
    private Short coinAmount;
}
