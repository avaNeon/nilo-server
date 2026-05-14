package com.neon.nilocommon.entity.po;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDateTime;


/**
 * 删除视频存档
 */
@Getter
@Setter
@ToString
public class VideoInfoArchive
{
    /**
     * 视频ID
     */
    private Long videoId;

    /**
     * 视频封面（路径要展示给前端，所以相对路径不带顶层目录）
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
     * 删除时间
     */
    private LocalDateTime deleteTime;

    /**
     * 删除者用户类型，0:用户，1:管理员
     */
    private Integer deleterType;

    /**
     * 删除详情
     */
    private String deleteDetail;

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
     * 标签，用","分隔不同的标签
     */
    private String tags;

    /**
     * 简介
     */
    private String introduction;

    /**
     * 互动设置（
     * 如果可以发弹幕和发评论，就是NULL；
     * 如果不能发弹幕，但是能发评论，就是0;
     * 如果能发弹幕，不能发评论，就是1;
     * 如果既不能发弹幕，也不能发评论，就是0,1
     * ）
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

}
