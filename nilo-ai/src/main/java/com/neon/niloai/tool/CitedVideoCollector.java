package com.neon.niloai.tool;

import com.neon.niloai.entity.vo.CitedVideoVO;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 引用视频收集器<hr/>
 * 每次请求新建一个，经 toolContext 传给工具。一次请求里 searchVideo 可能被调多次，
 * 这里按 videoId 去重并保持首次出现的顺序
 */
public class CitedVideoCollector
{
    private final Map <Long, CitedVideoVO> videos = new LinkedHashMap <>();

    synchronized void add(CitedVideoVO video)
    {
        videos.putIfAbsent(video.getVideoId(), video);
    }

    public synchronized List <CitedVideoVO> list()
    {
        return new ArrayList <>(videos.values());
    }
}
