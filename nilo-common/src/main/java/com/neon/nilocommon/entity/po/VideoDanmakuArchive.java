package com.neon.nilocommon.entity.po;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDateTime;


/**
 * 视频弹幕存档
 */
@Getter
@Setter
@ToString
public class VideoDanmakuArchive
{
    /**
     * 弹幕ID【对外展示】
     */
    private Long danmakuId;

    /**
     * 视频ID
     */
    private Long videoId;

    /**
     * 视频文件ID
     */
    private Long fileId;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 发布时间
     */
    private LocalDateTime postTime;

    /**
     * 内容
     */
    private String content;

    /**
     * 展示位置
     */
    private Integer position;

    /**
     * 颜色(HEX+不透明度)
     */
    private String color;

    /**
     * 展示时刻（单位：毫秒）
     */
    private Integer displayMoment;

}
