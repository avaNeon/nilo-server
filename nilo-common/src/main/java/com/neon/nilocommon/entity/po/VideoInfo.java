package com.neon.nilocommon.entity.po;

import cn.hutool.core.date.DateUtil;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.neon.nilocommon.entity.constants.DatePattern;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.util.Date;


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
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;

    /**
     * 最后更新时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date lastUpdateTime;

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
    private Integer postType;

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
    private Integer recommendType;

    /**
     * 最后播放时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date lastPlayTime;


    @Override
    public String toString()
    {
        return "视频ID:" + (videoId == null ? "空" : videoId) + "，视频封面:" + (videoCover == null ? "空" : videoCover) + "，视频名称:" + (videoName == null ? "空" : videoName) + "，用户ID:" + (userId == null ? "空" : userId) + "，创建时间:" + (createTime == null ? "空" : DateUtil.format(
                createTime,
                DatePattern.DATETIME)) + "，最后更新时间:" + (lastUpdateTime == null ? "空" : DateUtil.format(lastUpdateTime,
                                                                                                             DatePattern.DATETIME)) + "，父级分类ID:" + (pCategoryId == null ? "空" : pCategoryId) + "，分类ID:" + (categoryId == null ? "空" : categoryId) + "，0:自制作 1:转载:" + (postType == null ? "空" : postType) + "，原资源说明:" + (originInfo == null ? "空" : originInfo) + "，标签:" + (tags == null ? "空" : tags) + "，简介:" + (introduction == null ? "空" : introduction) + "，互动设置:" + (interaction == null ? "空" : interaction) + "，持续时间（秒）:" + (duration == null ? "空" : duration) + "，播放数量:" + (playCount == null ? "空" : playCount) + "，点赞数量:" + (likeCount == null ? "空" : likeCount) + "，弹幕数量:" + (danmakuCount == null ? "空" : danmakuCount) + "，评论数量:" + (commentCount == null ? "空" : commentCount) + "，投币数量:" + (coinCount == null ? "空" : coinCount) + "，收藏数量:" + (collectCount == null ? "空" : collectCount) + "，是否推荐0:未推荐 1:已推荐:" + (recommendType == null ? "空" : recommendType) + "，最后播放时间:" + (lastPlayTime == null ? "空" : DateUtil.format(
                lastPlayTime,
                DatePattern.DATETIME));
    }
}
