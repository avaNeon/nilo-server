package com.neon.niloweb.service;

import com.neon.nilocommon.entity.dto.VideoPlayHistoryDTO;
import com.neon.nilocommon.entity.enums.ResponseCode;
import com.neon.nilocommon.entity.po.VideoInfoFile;
import com.neon.nilocommon.entity.po.VideoPlayHistory;
import com.neon.nilocommon.entity.query.VideoInfoFileQuery;
import com.neon.nilocommon.entity.query.VideoPlayHistoryQuery;
import com.neon.nilocommon.exception.BusinessException;
import com.neon.nilocommon.util.PageCalculator;
import com.neon.niloweb.config.WebConfig;
import com.neon.niloweb.mapper.VideoInfoFileMapper;
import com.neon.niloweb.mapper.VideoPlayHistoryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@RequiredArgsConstructor
@Service
public class VideoPlayHistoryService
{
    private final VideoInfoFileMapper <VideoInfoFile, VideoInfoFileQuery> videoInfoFileMapper;

    private final VideoPlayHistoryMapper <VideoPlayHistory, VideoPlayHistoryQuery> videoPlayHistoryMapper;

    private final WebConfig webConfig;

    /**
     * 保存播放历史记录
     *
     * @param userId    用户ID
     * @param videoId   视频ID
     * @param fileIndex 文件索引
     */
    public void saveHistory(long userId, long videoId, int fileIndex)
    {
        // 校验一下是否真的存在这个视频文件
        VideoInfoFile videoInfoFile = videoInfoFileMapper.selectByVideoIdAndFileIndex(videoId, fileIndex);
        if (videoInfoFile == null)
        {
            throw new BusinessException(ResponseCode.INVALID_ARGUMENTS);
        }

        videoPlayHistoryMapper.recordVideoPlayHistory(userId, videoId, fileIndex, LocalDateTime.now());
    }

    /**
     * 分页查询播放历史
     *
     * @param userId 用户ID
     * @param pageNo 页号
     * @return 播放历史列表
     */
    public List <VideoPlayHistoryDTO> getHistoryList(long userId, int pageNo)
    {
        int pageSize = webConfig.getPlayHistoryPageSize();
        int start = (pageNo - 1) * pageSize;

        VideoPlayHistoryQuery query = new VideoPlayHistoryQuery();
        query.setUserId(userId);
        query.setOrderBy("v.last_update_time DESC");
        query.setPageCalculator(new PageCalculator(start, pageSize));

        return videoPlayHistoryMapper.selectDtoList(query);
    }

    /**
     * 删除单条播放历史
     *
     * @param userId  用户ID
     * @param videoId 视频ID
     */
    public void deleteHistory(long userId, long videoId)
    {
        videoPlayHistoryMapper.deleteByUserIdAndVideoId(userId, videoId);
    }

    /**
     * 删除用户全部播放历史
     *
     * @param userId 用户ID
     */
    public void deleteAllHistory(long userId)
    {
        videoPlayHistoryMapper.deleteByUserId(userId);
    }
}
