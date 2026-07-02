package com.neon.niloadmin.service;

import com.neon.niloadmin.mapper.VideoDanmakuMapper;
import com.neon.niloadmin.mapper.VideoInfoMapper;
import com.neon.niloadmin.repository.elasticsearch.VideoInfoDocRepository;
import com.neon.nilocommon.entity.po.VideoDanmaku;
import com.neon.nilocommon.entity.po.VideoInfo;
import com.neon.nilocommon.entity.query.VideoDanmakuQuery;
import com.neon.nilocommon.entity.query.VideoInfoQuery;
import com.neon.nilocommon.entity.vo.danmaku.DanmakuManagementVO;
import com.neon.nilocommon.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@RequiredArgsConstructor
@Service
public class VideoDanmakuService
{
    private final VideoDanmakuMapper <VideoDanmaku, VideoDanmakuQuery> videoDanmakuMapper;

    private final VideoInfoMapper <VideoInfo, VideoInfoQuery> videoInfoMapper;

    private final VideoInfoDocRepository videoInfoDocRepository;

    public Long getDanmakuManagementInfoCount(String nameFuzzy)
    {
        return videoDanmakuMapper.selectDanmakuCount(nameFuzzy);
    }

    public List <DanmakuManagementVO> getDanmakuManagementInfo(String nameFuzzy, Integer pageNo, Integer pageSize)
    {
        int start = (pageNo - 1) * pageSize;
        return videoDanmakuMapper.selectDanmakuManagementVO(nameFuzzy, start, pageSize);
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteDanmaku(Long danmakuId)
    {
        VideoDanmaku videoDanmaku = videoDanmakuMapper.selectByDanmakuId(danmakuId);
        if (videoDanmaku == null)
        {
            throw new BusinessException("不存在此条记录");
        }

        Integer deletedCount = videoDanmakuMapper.deleteByDanmakuId(danmakuId);
        if (deletedCount != null && deletedCount > 0)
        {
            Long videoId = videoDanmaku.getVideoId();
            videoInfoMapper.decreaseByField(videoId, "danmaku_count", 1);
            videoInfoDocRepository.decreaseDanmakuCountByVideoId(videoId, 1);
        }
        else
        {
            throw new BusinessException("未删除弹幕");
        }
    }
}
