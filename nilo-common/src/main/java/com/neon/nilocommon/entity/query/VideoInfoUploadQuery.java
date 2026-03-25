package com.neon.nilocommon.entity.query;


import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 视频信息参数
 */
@Schema(description = "查询视频信息参数")
@Setter
@Getter
public class VideoInfoUploadQuery extends BaseQuery
{

    /**
     * 排除的状态
     */
    private List<Short> exclusiveStatusList;

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
    private LocalDateTime createTime;

    /**
     * 起始时间（精度：日）
     */
    private LocalDate createTimeStart;

    /**
     * 截止时间（不包含，精度：日）
     */
    private LocalDate createTimeEnd;

    /**
     * 最后更新时间
     */
    private LocalDateTime lastUpdateTime;

    private LocalDate lastUpdateTimeStart;

    private LocalDate lastUpdateTimeEnd;

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
    private Short status;

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
