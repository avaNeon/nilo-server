package com.neon.niloai.entity.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/**
 * 流式问答的最后一条事件：正文以这段为准，视频和片段到这里才给前端渲染
 */
@Data
@AllArgsConstructor
public class AskStreamDoneVO
{
    private String answer;

    private List <CitedVideoVO> videos;

    private List <CitedSegmentVO> segments;
}
