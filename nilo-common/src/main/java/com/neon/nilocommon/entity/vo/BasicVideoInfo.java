package com.neon.nilocommon.entity.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 视频文件的最基础的信息<hr/>
 * 不包含视频外的信息，比如用户信息
 */
@Getter
@Setter
public abstract class BasicVideoInfo
{
    /**
     * 视频ID
     */
    protected Long videoId;

    /**
     * 视频封面
     */
    protected String videoCover;

    /**
     * 视频名称
     */
    protected String videoName;

    /**
     * 持续时间（秒）
     */
    protected Integer duration;

    /**
     * 播放数量
     */
    protected Integer playCount;

    /**
     * 弹幕数量
     */
    protected Integer danmakuCount;

    /**
     * 最后更新时间
     */
    protected LocalDateTime lastUpdateTime;

    /**
     * 父级分类编码
     */
    @JsonProperty("pCategoryNumber")
    protected String pCategoryNumber;

    /**
     * 分类编码
     */
    protected String categoryNumber;
}
