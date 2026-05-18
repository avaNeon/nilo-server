package com.neon.nilocommon.entity.vo.danmaku;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@ToString
@AllArgsConstructor
@NoArgsConstructor
public class DanmakuManagementVO
{
    /**
     * 弹幕ID
     */
    private Long danmakuId;

    /**
     * 视频ID
     */
    private Long videoId;

    /**
     * 视频文件序号
     */
    private Integer fileIndex;

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

    /**
     * 用户名
     */
    private String nickName;

    /**
     * 视频名称
     */
    private String videoName;
}
