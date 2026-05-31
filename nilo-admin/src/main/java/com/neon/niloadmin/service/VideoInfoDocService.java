package com.neon.niloadmin.service;

import com.neon.niloadmin.repository.elasticsearch.VideoInfoDocRepository;
import com.neon.nilocommon.entity.po.VideoInfo;
import com.neon.nilocommon.entity.po.document.VideoInfoDoc;
import com.neon.nilocommon.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
public class VideoInfoDocService
{
    private final VideoInfoDocRepository videoInfoDocRepository;

    /**
     * 向videoInfoDoc中完整 插入/更新 一条记录 <hr/>
     * 是插入还是更新取决于 videoId 是否存在于记录中<br/>
     * 必须包含完整的属性
     *
     * @param videoInfo videoInfo 数据源
     */
    public void saveVideoInfoDoc(VideoInfo videoInfo)
    {
        // 校验，若没有 videoId 则报错
        if (videoInfo.getVideoId() == null)
        {
            throw new BusinessException("videoId不能为空！");
        }

        // 赋值
        VideoInfoDoc videoInfoDoc = new VideoInfoDoc();
        BeanUtils.copyProperties(videoInfo, videoInfoDoc);

        // 查询ES记录
        VideoInfoDoc dbVideoInfoDoc = videoInfoDocRepository.findVideoInfoDocByVideoId(videoInfo.getVideoId());

        // 如果ES没有记录，就是插入操作，初始化统计数据
        if (dbVideoInfoDoc == null)
        {
            videoInfoDoc.setPlayCount(0);
            videoInfoDoc.setDanmakuCount(0);
            videoInfoDoc.setCollectCount(0);
        }

        // 入库
        videoInfoDocRepository.save(videoInfoDoc);
    }

}
