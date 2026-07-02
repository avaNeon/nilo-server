package com.neon.nilocommon.entity.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDateTime;

@ToString
@Setter
@Getter
public class VideoInfoUploadJoinDTO
{

    /**
     * 父级分类ID，内部使用不对外展示
     */
    @JsonProperty(value = "pCategoryId", access = JsonProperty.Access.WRITE_ONLY)
    private Integer pCategoryId;

    /**
     * 分类ID，内部使用不对外展示
     */
    @JsonProperty(value = "categoryId", access = JsonProperty.Access.WRITE_ONLY)
    private Integer categoryId;

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
    private String parentCategoryNumber;

    /**
     * 分类ID
     */
    private String categoryNumber;

    /**
     * 0:转码中 1:转码失败 2:待审核 3:审核成功 4:审核失败
     */
    private Short status;

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
}
