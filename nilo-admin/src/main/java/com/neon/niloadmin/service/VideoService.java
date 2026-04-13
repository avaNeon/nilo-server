package com.neon.niloadmin.service;

import com.neon.niloadmin.mapper.VideoInfoFileMapper;
import com.neon.niloadmin.mapper.VideoInfoFileUploadMapper;
import com.neon.niloadmin.mapper.VideoInfoMapper;
import com.neon.niloadmin.mapper.VideoInfoUploadMapper;
import com.neon.nilocommon.entity.dto.VideoInfoUploadAdminJoinDTO;
import com.neon.nilocommon.entity.enums.videoInfoFileUpload.UpdateType;
import com.neon.nilocommon.entity.enums.videoInfoUpload.VideoStatus;
import com.neon.nilocommon.entity.po.VideoInfo;
import com.neon.nilocommon.entity.po.VideoInfoFile;
import com.neon.nilocommon.entity.po.VideoInfoFileUpload;
import com.neon.nilocommon.entity.po.VideoInfoUpload;
import com.neon.nilocommon.entity.query.*;
import com.neon.nilocommon.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@RequiredArgsConstructor
@Service
public class VideoService
{

    private final VideoInfoUploadMapper <VideoInfoUpload, VideoInfoUploadQuery> videoInfoUploadMapper;

    private final VideoInfoFileUploadMapper <VideoInfoFileUpload, VideoInfoFileUploadQuery> videoInfoFileUploadMapper;

    private final VideoInfoMapper <VideoInfo, VideoInfoQuery> videoInfoMapper;

    private final VideoInfoFileMapper <VideoInfoFile, VideoInfoFileQuery> videoInfoFileMapper;

    /**
     * 查询视频
     *
     * @return 返回一个列表，审核成功的结果会有video_info的字段值
     */
    public List <VideoInfoUploadAdminJoinDTO> loadVideoList(VideoInfoUploadQuery infoUploadQuery)
    {
        infoUploadQuery.setOrderBy("v.create_time desc");
        // 分页查询
        Integer count = videoInfoUploadMapper.selectCount(infoUploadQuery);
        infoUploadQuery.setPageCalculator(new PageCalculator(infoUploadQuery.getPageNo(), count, infoUploadQuery.getPageSize()));
        return videoInfoUploadMapper.selectListWithVideoInfoWithUserInfo(infoUploadQuery);
    }

    /**
     * 审核视频
     *
     * @param videoId      视频id
     * @param reviewResult 审核结果
     * @param refuseReason 拒绝原因
     */
    @Transactional(rollbackFor = Exception.class)
    public void reviewVideo(long videoId, boolean reviewResult, String refuseReason)// todo将拒绝原因发送给用户
    {
        VideoInfoUpload addedInfoUpload = new VideoInfoUpload();
        addedInfoUpload.setStatus(reviewResult ? VideoStatus.REVIEW_SUCCESS.getStatus() : VideoStatus.REVIEW_FAILED.getStatus()); // 审核状态

        VideoInfoUploadQuery infoUploadQuery = new VideoInfoUploadQuery();
        infoUploadQuery.setStatus(VideoStatus.PENDING_REVIEW.getStatus()); // 保证status为2的视频才能被更新，避免重复更新，防止并发问题
        infoUploadQuery.setVideoId(videoId);

        Integer updateCount = videoInfoUploadMapper.updateByParam(addedInfoUpload, infoUploadQuery);
        if (updateCount == 0)
        {
            throw new BusinessException("审核失败！");
        }

        VideoInfoFileUpload addedFileUpload = new VideoInfoFileUpload();
        addedFileUpload.setUpdateType(UpdateType.NO_UPDATE.getUpdateType());

        VideoInfoFileUploadQuery fileUploadQuery = new VideoInfoFileUploadQuery();
        fileUploadQuery.setVideoId(videoId);

        videoInfoFileUploadMapper.updateByParam(addedFileUpload, fileUploadQuery);

        // 如果审核不通过，就不继续将VideoInfoUpload移动到VideoInfo了
        if (!reviewResult) return;

        VideoInfoUpload infoUpload = videoInfoUploadMapper.selectByVideoId(videoId);

        VideoInfo videoInfo = videoInfoMapper.selectByVideoId(videoId);
        if (videoInfo == null)
        {
            videoInfo = new VideoInfo();
            //todo 给用户加硬币
        }

        // 更新/填入videoInfo信息
        BeanUtils.copyProperties(infoUpload, videoInfo);
        videoInfoMapper.insertOrUpdate(videoInfo);

        // 删除旧的video_info_file记录（DB层面）
        VideoInfoFileQuery infoFileQuery = new VideoInfoFileQuery();
        infoFileQuery.setVideoId(videoId);
        videoInfoFileMapper.deleteByParam(infoFileQuery);

        // 获取videoInfoFileUpload记录
        VideoInfoFileUploadQuery videoInfoFileUploadQuery = new VideoInfoFileUploadQuery();
        videoInfoFileUploadQuery.setVideoId(videoId);
        List <VideoInfoFileUpload> infoFileUploadList = videoInfoFileUploadMapper.selectList(videoInfoFileUploadQuery);

        // 将获取的记录转化之后插入到video_info_file表
        List <VideoInfoFile> infoFileList = infoFileUploadList.stream().map(infoFileUpload ->
                                                                            {
                                                                                VideoInfoFile infoFile = new VideoInfoFile();
                                                                                BeanUtils.copyProperties(infoFileUpload, infoFile);
                                                                                return infoFile;
                                                                            }).toList();
        videoInfoFileMapper.insertBatch(infoFileList);

        // 注意：不在此处删除物理文件，因为：
        // 1. 用户修改视频时，videoUpload(CreativeCenterService)已经处理了文件删除
        // 2. 未修改的文件在新旧记录中路径相同，删除会导致数据丢失
        // 3. 只有用户明确移除的文件才应该被删除，这已在上传阶段处理

        //todo 保存信息到ES中
    }
}
