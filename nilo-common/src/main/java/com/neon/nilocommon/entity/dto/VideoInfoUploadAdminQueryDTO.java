package com.neon.nilocommon.entity.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.List;

@Schema(description = "后台视频列表查询参数")
@Getter
@Setter
public class VideoInfoUploadAdminQueryDTO
{
    /**
     * 页号（从1开始）
     */
    @NotNull
    @Min(1)
    private Integer pageNo;

    /**
     * 页大小
     */
    @Max(20)
    @Min(1)
    private Integer pageSize;

    /**
     * 排除的状态
     */
    private List <Short> exclusiveStatusList;

    /**
     * 视频ID
     */
    private Long videoId;

    /**
     * 视频名称模糊查询
     */
    private String videoNameFuzzy;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 创建时间起始日期
     */
    private LocalDate createTimeStart;

    /**
     * 创建时间截止日期（不包含）
     */
    private LocalDate createTimeEnd;

    /**
     * 最后更新时间起始日期
     */
    private LocalDate lastUpdateTimeStart;

    /**
     * 最后更新时间截止日期（不包含）
     */
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
     * 标签模糊查询
     */
    private String tagsFuzzy;

    /**
     * 简介模糊查询
     */
    private String introductionFuzzy;

    /**
     * 互动设置
     */
    private String interaction;
}
