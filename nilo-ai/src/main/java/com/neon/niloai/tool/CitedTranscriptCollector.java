package com.neon.niloai.tool;

import com.neon.niloai.entity.vo.TranscriptHitVO;

import java.util.ArrayList;
import java.util.List;

/**
 * 字幕命中收集器<hr/>
 * 每次请求新建一个，经 toolContext 传给工具。调用结束后用它核对回答里的时间点：
 * 只有落在某个命中块时间范围里的时间点才会返回给前端，模型编的时间点跳不过去
 */
public class CitedTranscriptCollector
{
    private final List <TranscriptHitVO> hits = new ArrayList <>();

    synchronized void addAll(List <TranscriptHitVO> newHits)
    {
        hits.addAll(newHits);
    }

    public synchronized List <TranscriptHitVO> list()
    {
        return new ArrayList <>(hits);
    }
}
