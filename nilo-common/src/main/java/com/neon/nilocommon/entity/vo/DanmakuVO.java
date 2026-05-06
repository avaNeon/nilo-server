package com.neon.nilocommon.entity.vo;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDateTime;

@Getter
@Setter
@ToString
public class DanmakuVO
{
    /**
     * 弹幕ID【对外展示】
     */
    private Long danmakuId;

    /**
     * 用户ID
     */
    private Long userId;

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

    /**
     * 发布时间
     */
    private LocalDateTime postTime;
}
