package com.neon.nilocommon.entity.query;


import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 删除视频存档参数
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class VideoInfoArchiveQuery extends BaseQuery
{
    private Long videoId;


    private String videoCover;

    private String videoCoverFuzzy;

    /**
     * 视频名称
     */
    private String videoName;

    private String videoNameFuzzy;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 创建时间
     */
    private String createTime;

    private String createTimeStart;

    private String createTimeEnd;

    /**
     * 最后更新时间
     */
    private String lastUpdateTime;

    private String lastUpdateTimeStart;

    private String lastUpdateTimeEnd;

    /**
     * 删除时间
     */
    private String deleteTime;

    private String deleteTimeStart;

    private String deleteTimeEnd;

    /**
     * 删除者用户类型，0:用户，1:管理员
     */
    private Integer deleterType;

    /**
     * 删除详情
     */
    private String deleteDetail;

    private String deleteDetailFuzzy;

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

    private String originInfoFuzzy;

    /**
     * 标签，用","分隔不同的标签
     */
    private String tags;

    private String tagsFuzzy;

    /**
     * 简介
     */
    private String introduction;

    private String introductionFuzzy;

    /**
     * 互动设置（
     * 如果可以发弹幕和发评论，就是NULL；
     * 如果不能发弹幕，但是能发评论，就是0;
     * 如果能发弹幕，不能发评论，就是1;
     * 如果既不能发弹幕，也不能发评论，就是0,1
     * ）
     */
    private String interaction;

    private String interactionFuzzy;

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
