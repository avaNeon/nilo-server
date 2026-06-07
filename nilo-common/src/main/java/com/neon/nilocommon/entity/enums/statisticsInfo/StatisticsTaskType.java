package com.neon.nilocommon.entity.enums.statisticsInfo;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum StatisticsTaskType
{
    PULL_VIDEO_PLAY_DAILY("PULL_VIDEO_PLAY_DAILY", "视频每日播放量入库"),
    REDUCE_PLAY_DAILY_STATISTICS("REDUCE_PLAY_DAILY_STATISTICS", "聚合用户每日播放量"),
    DELETE_VIDEO_PLAY_DAILY("DELETE_VIDEO_PLAY_DAILY", "删除视频每日播放量数据"),
    FOLLOWER_STATISTICS("FOLLOWER_STATISTICS", "每日新增粉丝统计"),
    COMMENT_STATISTICS("COMMENT_STATISTICS", "每日评论统计"),
    DANMAKU_STATISTICS("DANMAKU_STATISTICS", "每日弹幕统计"),
    VIDEO_ACTION_STATISTICS("VIDEO_ACTION_STATISTICS", "每日点赞、收藏、投币统计"),
    DELETE_EXPIRED_STATISTICS("DELETE_EXPIRED_STATISTICS", "删除过期统计数据");

    private final String label;
    private final String description;
}
