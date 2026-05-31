package com.neon.niloweb;

import com.neon.nilocommon.entity.po.VideoInfo;
import com.neon.nilocommon.entity.query.VideoInfoQuery;
import com.neon.niloweb.mapper.VideoInfoMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class EsTest
{
    @Autowired
    private VideoInfoMapper <VideoInfo, VideoInfoQuery> videoInfoMapper;

//    @Test
//    public void test1()
//    {
//        VideoInfo videoInfo = videoInfoMapper.selectByVideoId(2035685339086979072L);
//        VideoInfoDoc videoInfoDoc = new VideoInfoDoc();
//        BeanUtils.copyProperties(videoInfo, videoInfoDoc);
//        videoInfoDocService.saveVideoInfoDoc(videoInfoDoc);
//    }
//
//    @Test
//    public void test2()
//    {
//        List <VideoInfoDoc> videoInfoDocs = videoInfoDocService.selectAllVideoInfoDoc();
//        for (VideoInfoDoc videoInfoDoc : videoInfoDocs)
//        {
//            System.out.println(videoInfoDoc);
//        }
//    }

}
