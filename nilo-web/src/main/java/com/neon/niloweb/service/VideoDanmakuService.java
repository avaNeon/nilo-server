package com.neon.niloweb.service;

import cn.hutool.core.lang.Snowflake;
import com.neon.nilocommon.entity.dto.DanmakuDTO;
import com.neon.nilocommon.entity.enums.ResponseCode;
import com.neon.nilocommon.entity.po.VideoDanmaku;
import com.neon.nilocommon.entity.po.VideoInfo;
import com.neon.nilocommon.entity.po.VideoInfoFile;
import com.neon.nilocommon.entity.query.VideoDanmakuQuery;
import com.neon.nilocommon.entity.query.VideoInfoFileQuery;
import com.neon.nilocommon.entity.query.VideoInfoQuery;
import com.neon.nilocommon.entity.vo.danmaku.DanmakuVO;
import com.neon.nilocommon.exception.BusinessException;
import com.neon.niloweb.mapper.VideoDanmakuMapper;
import com.neon.niloweb.mapper.VideoInfoFileMapper;
import com.neon.niloweb.mapper.VideoInfoMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;


/**
 * 视频弹幕 业务接口实现
 */
@RequiredArgsConstructor
@Service()
public class VideoDanmakuService
{
    /**
     * 单次加载允许的最大时间窗口（毫秒）
     */
    private static final int MAX_LOAD_WINDOW_MS = 5_000;

    private final VideoInfoMapper <VideoInfo, VideoInfoQuery> videoInfoMapper;

    private final VideoDanmakuMapper <VideoDanmaku, VideoDanmakuQuery> videoDanmakuMapper;

    private final VideoInfoFileMapper <VideoInfoFile, VideoInfoFileQuery> videoInfoFileMapper;

    private final Snowflake snowflake;

    /**
     * 发布弹幕
     *
     * @param userId     用户ID
     * @param danmakuDTO 弹幕DTO
     */
    @Transactional(rollbackFor = Exception.class)
    public void postDanmaku(long userId, DanmakuDTO danmakuDTO)
    {
        // --------
        // 通过videoId和index找出fileId
        // --------
        VideoInfoFileQuery videoInfoFileQuery = new VideoInfoFileQuery();
        videoInfoFileQuery.setVideoId(danmakuDTO.getVideoId());
        videoInfoFileQuery.setFileIndex(danmakuDTO.getFileIndex());
        List <VideoInfoFile> videoInfoFileList = videoInfoFileMapper.selectList(videoInfoFileQuery);

        VideoDanmaku videoDanmaku = new VideoDanmaku();
        BeanUtils.copyProperties(danmakuDTO, videoDanmaku);
        videoDanmaku.setFileId(videoInfoFileList.get(0).getFileId());

        // --------
        // 校验
        // --------
        Long videoId = videoDanmaku.getVideoId();
        VideoInfo videoInfo = videoInfoMapper.selectByVideoId(videoId);
        // 校验视频是否存在
        if (videoInfo == null)
        {
            throw new BusinessException(ResponseCode.NOT_FOUND);
        }
        // 校验弹幕限制
        String interaction = videoInfo.getInteraction();
        if (interaction != null && (interaction.equals("0") || interaction.equals("0,1")))
        {
            throw new BusinessException("功能禁用");
        }

        // --------
        // 数据入库
        // --------
        // 设置userId
        videoDanmaku.setUserId(userId);
        // 设置danmakuId
        videoDanmaku.setDanmakuId(snowflake.nextId());
        // 设置发布时间
        videoDanmaku.setPostTime(LocalDateTime.now());

        // 入库
        videoDanmakuMapper.insert(videoDanmaku);

        // 更新弹幕数量
        videoInfoMapper.increaseByField(videoId, "danmaku_count", 1);
    }

    /**
     * 按视频时间轴区间加载弹幕<hr/>
     * <p>区间为左闭右开 [fromMs, toMs)，单次跨度不得超过 {@link #MAX_LOAD_WINDOW_MS} 毫秒。</p>
     *
     * @param videoId   视频ID
     * @param fileIndex 分片索引
     * @param fromMs    起始展示时刻（毫秒，含）
     * @param toMs      结束展示时刻（毫秒，不含）
     * @return 弹幕VO
     */
    public List <DanmakuVO> loadDanmaku(long videoId, int fileIndex, int fromMs, int toMs)
    {
        if (fromMs < 0 || toMs < 0 || toMs <= fromMs || (long) toMs - fromMs > MAX_LOAD_WINDOW_MS)
        {
            throw new BusinessException(ResponseCode.WRONG_ARGUMENTS);
        }

        // --------
        // 校验
        // --------
        VideoInfo videoInfo = videoInfoMapper.selectByVideoId(videoId);
        // 校验视频是否存在
        if (videoInfo == null)
        {
            throw new BusinessException(ResponseCode.NOT_FOUND);
        }
        // 校验弹幕限制
        String interaction = videoInfo.getInteraction();
        if (interaction != null && (interaction.equals("0") || interaction.equals("0,1")))
        {
            return new ArrayList <>();
        }

        VideoInfoFileQuery videoInfoFileQuery = new VideoInfoFileQuery();
        videoInfoFileQuery.setVideoId(videoId);
        videoInfoFileQuery.setFileIndex(fileIndex);
        List <VideoInfoFile> videoInfoFileList = videoInfoFileMapper.selectList(videoInfoFileQuery);
        if (videoInfoFileList == null || videoInfoFileList.isEmpty())
        {
            throw new BusinessException(ResponseCode.NOT_FOUND);
        }

        VideoDanmakuQuery danmakuQuery = new VideoDanmakuQuery();
        danmakuQuery.setVideoId(videoId);
        danmakuQuery.setFileId(videoInfoFileList.get(0).getFileId());
        danmakuQuery.setDisplayMomentStart(fromMs);
        danmakuQuery.setDisplayMomentEnd(toMs);
        danmakuQuery.setOrderBy("display_moment ASC");

        return findListByParam(danmakuQuery).stream().map(danmaku ->
                                                          {
                                                              DanmakuVO danmakuVO = new DanmakuVO();
                                                              BeanUtils.copyProperties(danmaku, danmakuVO);
                                                              return danmakuVO;
                                                          }).toList();
    }

    /**
     * 删除弹幕
     *
     * @param userId    用户ID
     * @param danmakuId 弹幕ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteDanmaku(long userId, long danmakuId)
    {
        VideoDanmaku videoDanmaku = videoDanmakuMapper.selectByDanmakuId(danmakuId);

        // 不能删除不存在的弹幕
        if (videoDanmaku == null)
        {
            throw new BusinessException(ResponseCode.NOT_FOUND);
        }
        VideoInfo videoInfo = videoInfoMapper.selectByVideoId(videoDanmaku.getVideoId());

        // 只能删除 自己的弹幕 或者 自己视频下的弹幕
        if (videoDanmaku.getUserId() != userId && videoInfo.getUserId() != userId)
        {
            throw new BusinessException(ResponseCode.NOT_FOUND);
        }

        Integer deletedCount = videoDanmakuMapper.deleteByDanmakuId(danmakuId);

        if (deletedCount != null && deletedCount > 0)
        {
            videoInfoMapper.decreaseByField(videoDanmaku.getVideoId(), "danmaku_count", 1);
        }
        else
        {
            throw new BusinessException("删除弹幕失败");
        }
    }

    /**
     * 根据条件查询列表
     */
    public List <VideoDanmaku> findListByParam(VideoDanmakuQuery param)
    {
        return this.videoDanmakuMapper.selectList(param);
    }

    /**
     * 新增
     */
    public Integer add(VideoDanmaku bean)
    {
        return this.videoDanmakuMapper.insert(bean);
    }

    /**
     * 多条件更新
     */
    public Integer updateByParam(VideoDanmaku bean, VideoDanmakuQuery param)
    {
        return this.videoDanmakuMapper.updateByParam(bean, param);
    }

    /**
     * 多条件删除
     */
    public Integer deleteByParam(VideoDanmakuQuery param)
    {
        return this.videoDanmakuMapper.deleteByParam(param);
    }

}