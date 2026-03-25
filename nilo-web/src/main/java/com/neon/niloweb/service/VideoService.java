package com.neon.niloweb.service;

import com.neon.nilocommon.entity.po.VideoInfo;
import com.neon.nilocommon.entity.query.VideoInfoQuery;
import com.neon.niloweb.mapper.VideoInfoMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@RequiredArgsConstructor
@Service
public class VideoService
{
    private final VideoInfoMapper <VideoInfo, VideoInfoQuery> videoInfoMapper;

    /**
     * 根据参数查询所有VideoInfo
     * @param infoQuery 查询参数
     * @return 查询结果
     */
    public List <VideoInfo> selectList(VideoInfoQuery infoQuery)
    {
        return videoInfoMapper.selectList(infoQuery);
    }
}
