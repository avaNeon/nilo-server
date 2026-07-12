package com.neon.nilocommon.entity.po;

import cn.hutool.core.date.DateUtil;
import com.neon.nilocommon.entity.constants.DatePattern;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;


/**
 * 视频信息
 */
@Setter
@Getter
public class VideoInfo
{
    /**
     * 视频ID
     */
    private Long videoId;

    /**
     * 视频封面
     */
    private String videoCover;

    /**
     * 视频名称
     */
    private String videoName;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 最后更新时间
     */
    private LocalDateTime lastUpdateTime;

    /**
     * 父级分类ID
     */
    private Integer pCategoryId;

    /**
     * 分类ID
     */
    private Integer categoryId;

    /**
     * 0:自制作 1:转载
     */
    private Short postType;

    /**
     * 原资源说明
     */
    private String originInfo;

    /**
     * 标签
     */
    private String tags;

    /**
     * 简介
     */
    private String introduction;

    /**
     * 互动设置
     */
    private String interaction;

    /**
     * 持续时间（秒）
     */
    private Integer duration;

    /**
     * 播放数量
     */
    private Integer playCount;

    /**
     * 点赞数量
     */
    private Integer likeCount;

    /**
     * 弹幕数量
     */
    private Integer danmakuCount;

    /**
     * 评论数量
     */
    private Integer commentCount;

    /**
     * 投币数量
     */
    private Integer coinCount;

    /**
     * 收藏数量
     */
    private Integer collectCount;

    /**
     * 是否推荐0:未推荐 1:已推荐
     */
    private Short recommendType;

    /**
     * 最后播放时间
     */
    private LocalDateTime lastPlayTime;

}
