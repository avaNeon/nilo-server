package com.neon.nilocommon.entity.query;


import lombok.Getter;
import lombok.Setter;

/**
 * 视频信息参数
 */
@Setter
@Getter
public class VideoInfoUploadQuery extends BaseQuery
{

    /**
     * 视频ID
     */
    private Long videoId;

    /**
     * 视频封面
     */
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
     * 父级分类ID
     */
    private Integer pCategoryId;

    /**
     * 分类ID
     */
    private Integer categoryId;

    /**
     * 0:转码中 1:转码失败 2:待审核 3:审核成功 4:审核失败
     */
    private Integer status;

    /**
     * 0:自制作 1:转载
     */
    private Integer postType;

    /**
     * 原资源说明
     */
    private String originInfo;

    private String originInfoFuzzy;

    /**
     * 标签
     */
    private String tags;

    private String tagsFuzzy;

    /**
     * 简介
     */
    private String introduction;

    private String introductionFuzzy;

    /**
     * 互动设置
     */
    private String interaction;

    private String interactionFuzzy;

    /**
     * 持续时间（秒）
     */
    private Integer duration;


}
