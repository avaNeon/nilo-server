package com.neon.niloai.entity.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 回答所引用的检索视频
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CitedVideoVO
{
    private Long videoId;

    private String videoName;
}
