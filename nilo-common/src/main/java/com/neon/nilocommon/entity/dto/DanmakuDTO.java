package com.neon.nilocommon.entity.dto;

import jakarta.validation.constraints.*;
import lombok.*;

@Getter
@Setter
@ToString
@AllArgsConstructor
@NoArgsConstructor
public class DanmakuDTO
{
    /**
     * 视频ID
     */
    @NotNull
    private Long videoId;

    /**
     * 分片索引
     */
    @NotNull
    private Integer fileIndex;

    /**
     * 内容
     */
    @NotEmpty
    @Size(max = 500)
    private String content;

    /**
     * 展示位置
     */
    @NotNull
    private Integer position;

    /**
     * 颜色(HEX+不透明度)
     */
    @NotEmpty
    @Size(max = 9)
    private String color;

    /**
     * 展示时刻（单位：毫秒）
     */
    @NotNull
    @Min(0L)
    private Integer displayMoment;
}
