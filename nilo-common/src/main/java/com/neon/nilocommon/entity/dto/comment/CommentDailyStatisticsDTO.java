package com.neon.nilocommon.entity.dto.comment;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 视频作者每日收到的评论数聚合结果
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CommentDailyStatisticsDTO
{
    /**
     * 视频作者用户ID（video_comment.video_user_id）
     */
    private Long userId;

    /**
     * 当日收到的评论数
     */
    private Integer statisticsCount;
}
