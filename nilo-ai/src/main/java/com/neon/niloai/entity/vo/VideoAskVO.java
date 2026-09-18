package com.neon.niloai.entity.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 视频问答结果
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class VideoAskVO
{
    private String answer;

    private List <CitedVideoVO> videos;
}
