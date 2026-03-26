package com.neon.nilocommon.entity.vo;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDateTime;

/**
 * 展示给用户的视频信息
 */
@Getter
@Setter
@ToString
public class VideoInfoVO extends BasicVideoInfo
{
    /**
     * 用户信息
     */
    private UserInfoVO userInfo;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

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
     * 点赞数量
     */
    private Integer likeCount;

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
}
