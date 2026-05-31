package com.neon.niloadmin;

import com.neon.niloadmin.mapper.VideoInfoMapper;
import com.neon.niloadmin.service.VideoInfoDocService;
import com.neon.nilocommon.entity.po.VideoInfo;
import com.neon.nilocommon.entity.query.VideoInfoQuery;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

@SpringBootTest
@Disabled("Manual data migration test; do not run during regular builds.")
public class MoveVideoInfoDataTest
{
    @Autowired
    private VideoInfoMapper <VideoInfo, VideoInfoQuery> videoInfoMapper;

    @Autowired
    private VideoInfoDocService videoInfoDocService;

    @Test
    public void moveAllVideos()
    {
        List <VideoInfo> videoInfoList = videoInfoMapper.selectList(new VideoInfoQuery());
        videoInfoList.forEach(videoInfoDocService::saveVideoInfoDoc);
    }

}
