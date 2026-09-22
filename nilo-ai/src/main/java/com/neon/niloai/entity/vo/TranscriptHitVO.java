package com.neon.niloai.entity.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 字幕检索命中<hr/>
 * searchTranscript 工具返回给模型的一个字幕块
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TranscriptHitVO
{
    private Long videoId;

    private String videoName;

    /**
     * 第几P，从 1 开始
     */
    private Integer fileIndex;

    /**
     * lines 覆盖的起止时间，单位：秒，从这一P开头算起
     */
    private Integer startSec;

    private Integer endSec;

    /**
     * 这一块的字幕，一条一行，每行开头是这条字幕出现的时间，比如「[5:29] OBS Studio is ...」
     */
    private String lines;
}
