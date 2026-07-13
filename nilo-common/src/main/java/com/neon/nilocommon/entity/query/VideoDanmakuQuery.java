package com.neon.nilocommon.entity.query;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;


/**
 * 视频弹幕参数
 */
@Getter
@Setter
@ToString
public class VideoDanmakuQuery extends BaseQuery
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
    private String postTime;

    private String postTimeStart;

    private String postTimeEnd;

    /**
     * 内容
     */
    private String content;

    private String contentFuzzy;

    /**
     * 展示位置
     */
    private Integer position;

    /**
     * 颜色(HEX+不透明度)
     */
    private String color;

    private String colorFuzzy;

    /**
     * 展示时刻（单位：毫秒）
     */
    private Integer displayMoment;

    /**
     * 展示时刻起始（单位：毫秒，闭区间）
     */
    private Integer displayMomentStart;

    /**
     * 展示时刻结束（单位：毫秒，开区间）
     */
    private Integer displayMomentEnd;

}
