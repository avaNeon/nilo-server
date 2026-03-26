package com.neon.niloweb.service;

import com.neon.nilocommon.entity.enums.ResponseCode;
import com.neon.nilocommon.entity.enums.videoInfo.RecommendType;
import com.neon.nilocommon.entity.po.VideoInfo;
import com.neon.nilocommon.entity.po.VideoInfoFile;
import com.neon.nilocommon.entity.query.PageCalculator;
import com.neon.nilocommon.entity.query.VideoInfoFileQuery;
import com.neon.nilocommon.entity.query.VideoInfoQuery;
import com.neon.nilocommon.entity.vo.BriefVideoInfoVO;
import com.neon.nilocommon.entity.vo.VideoInfoFileVO;
import com.neon.nilocommon.entity.vo.VideoInfoVO;
import com.neon.nilocommon.exception.BusinessException;
import com.neon.niloweb.config.WebConfig;
import com.neon.niloweb.mapper.VideoInfoFileMapper;
import com.neon.niloweb.mapper.VideoInfoMapper;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@RequiredArgsConstructor
@Service
public class VideoService
{

    private final VideoInfoMapper <VideoInfo, VideoInfoQuery> videoInfoMapper;

    private final VideoInfoFileMapper <VideoInfoFile, VideoInfoFileQuery> videoInfoFileMapper;

    private final WebConfig webConfig;

    /**
     * 获取所有推荐视频<hr/>
     * <ul>
     *     <li>时间顺序从新到旧</li>
     * </ul>
     *
     * @return 推荐视频列表
     */
    public List <BriefVideoInfoVO> loadRecommendVideo()
    {
        VideoInfoQuery infoQuery = new VideoInfoQuery();
        infoQuery.setOrderBy("create_time desc");
        infoQuery.setRecommendType(RecommendType.RECOMMENDED.getType());
        return videoInfoMapper.selectBriefVoListByParam(infoQuery);
    }

    /**
     * 获取视频列表<hr/>
     * <ul>
     *     <li>时间顺序从新到旧</li>
     * </ul>
     *
     * @return 视频列表
     */
    public List <BriefVideoInfoVO> loadVideo(Integer parentCategoryId, Integer categoryId, Integer pageNo)
    {
        VideoInfoQuery infoQuery = new VideoInfoQuery();
        infoQuery.setOrderBy("create_time desc");
        infoQuery.setRecommendType(RecommendType.UNRECOMMENDED.getType());
        infoQuery.setPCategoryId(parentCategoryId);
        infoQuery.setCategoryId(categoryId);
        Integer count = videoInfoMapper.selectCount(infoQuery);
        infoQuery.setPageCalculator(new PageCalculator(pageNo, count, webConfig.getPageSize()));
        return videoInfoMapper.selectBriefVoListByParam(infoQuery);
    }

    /**
     * 获取视频详细信息
     * @param videoId 视频ID
     * @return 视频VO
     */
    public VideoInfoVO loadVideoInfo(long videoId)
    {
        VideoInfoVO videoInfoVO = videoInfoMapper.selectVoByVideoId(videoId);
        if (videoInfoVO == null)
        {
            throw new BusinessException(ResponseCode.NOT_FOUND);
        }
        //todo 获取用户行为：点赞、投币、收藏
        return videoInfoVO;
    }

    /**
     * 获取分P文件信息
     * @param videoId 视频ID
     * @return 所有分P文件信息
     */
    public List<VideoInfoFileVO> loadVideoFile(@NotNull Long videoId)
    {
        List <VideoInfoFileVO> partitionFiles = videoInfoFileMapper.selectVoByVideoID(videoId);
        if(partitionFiles == null || partitionFiles.isEmpty())
        {
            throw new BusinessException(ResponseCode.NOT_FOUND);
        }
        return partitionFiles;
    }
}
