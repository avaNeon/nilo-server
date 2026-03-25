package com.neon.niloweb.service;

import cn.hutool.core.lang.Snowflake;
import com.neon.nilocommon.entity.dto.TokenUserInfo;
import com.neon.nilocommon.entity.dto.VideoInfoUploadJoinDTO;
import com.neon.nilocommon.entity.enums.videoInfoFileUpload.VideoFileStatus;
import com.neon.nilocommon.entity.enums.videoInfoUpload.VideoStatus;
import com.neon.nilocommon.entity.po.VideoInfoFileUpload;
import com.neon.nilocommon.entity.po.VideoInfoUpload;
import com.neon.nilocommon.entity.query.PageCalculator;
import com.neon.nilocommon.entity.query.VideoInfoFileUploadQuery;
import com.neon.nilocommon.entity.query.VideoInfoUploadQuery;
import com.neon.nilocommon.entity.vo.VideoStatusCountVO;
import com.neon.nilocommon.exception.BusinessException;
import com.neon.niloweb.config.SystemConfig;
import com.neon.niloweb.mapper.VideoInfoFileUploadMapper;
import com.neon.niloweb.mapper.VideoInfoUploadMapper;
import com.neon.niloweb.repository.rabbitmq.MqRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Service
public class CreativeCenterService
{
    private final SystemConfig systemConfig;

    private final VideoInfoUploadMapper <VideoInfoUpload, VideoInfoUploadQuery> videoInfoUploadMapper;

    private final VideoInfoFileUploadMapper <VideoInfoFileUpload, VideoInfoFileUploadQuery> videoInfoFileUploadMapper;

    private final Snowflake snowflake;

    private final MqRepository mqRepository;

    /**
     * 视频上传<hr/>
     * 视频上传流程：<br/>
     * <li> 1. 先将字段整合成videoInfoUpload类 </li>
     * <li> 2. 检验分P数是否在合适范围内 </li>
     * <li> 3. 分支，如果是是新视频，就将视频信息记录和视频文件记录保存在mysql中，并将视频文件全部交给MQ转码 </li>
     * <li> 4. 分支，如果是提交过的视频做修改，如果这个视频没有转码完成或审核完成，就不能继续修改。如果可以修改，那么会上传新出现的视频文件，删除未出现的视频文件。如果视频文件相同，那么就修改一下序号。 </li>
     * <hr/>
     * 填写了 VideoInfoFileUpload 的 file_id, user_id, video_id, file_index, update_type, transfer_result 这几个字段
     */
    @Transactional(rollbackFor = Exception.class)
    public void videoUpload(Long videoId,
                            String coverPathStr,
                            String videoTitle,
                            Integer pCategoryId,
                            Integer categoryId,
                            Short postType,
                            String tags,
                            String introduction,
                            String interaction,
                            List <VideoInfoFileUpload> uploadFileList,
                            TokenUserInfo tokenUserInfo)
    {
        Long userId = tokenUserInfo.getUserId();
        // 将传入的参数赋值给视频信息对象
        VideoInfoUpload videoInfoUpload = new VideoInfoUpload();
        videoInfoUpload.setVideoId(videoId);
        videoInfoUpload.setVideoCover(coverPathStr);
        videoInfoUpload.setVideoName(videoTitle);
        videoInfoUpload.setUserId(userId);
        videoInfoUpload.setPCategoryId(pCategoryId);
        videoInfoUpload.setCategoryId(categoryId);
        videoInfoUpload.setPostType(postType);
        videoInfoUpload.setTags(tags);
        videoInfoUpload.setIntroduction(introduction);
        videoInfoUpload.setInteraction(interaction);

        // 检查分P数是否在合理范围内
        if (uploadFileList.size() > systemConfig.getVideoMaxEpisodes())
        {
            throw new BusinessException("分P数过多");
        }

        LocalDateTime curDate = LocalDateTime.now(); // 先定义一个当前时间，后面用

        // 新增情况
        if (videoId == null)
        {
            videoId = snowflake.nextId();
            videoInfoUpload.setVideoId(videoId);
            videoInfoUpload.setCreateTime(curDate);
            videoInfoUpload.setLastUpdateTime(curDate);
            videoInfoUpload.setStatus(VideoStatus.TRANSCODING.getStatus());
            videoInfoUploadMapper.insert(videoInfoUpload);

            // 新增视频文件记录
            int index = 1;
            for (VideoInfoFileUpload uploadFile : uploadFileList)
            {
                uploadFile.setFileIndex(index++);
                uploadFile.setVideoId(videoId);
                uploadFile.setUserId(videoInfoUpload.getUserId());
                uploadFile.setFileId(snowflake.nextId());
                uploadFile.setUpdateType((short) 1);
                uploadFile.setTransferResult(VideoFileStatus.TRANSCODING.getStatus());
            }
            videoInfoFileUploadMapper.insertBatch(uploadFileList);
            mqRepository.addVideoFile2TranscodingQueue(uploadFileList);
        }
        else // 修改情况
        {
            /* 检查部分 */
            // 修改操作的合法性检查
            VideoInfoUpload videoInfoUploadDb = videoInfoUploadMapper.selectByVideoId(videoId);
            // 不能修改没有存在的视频信息
            if (videoInfoUploadDb == null)
            {
                throw new BusinessException("未存储此数据");
            }
            // 不允许修改别人的视频信息
            if (!Objects.equals(videoInfoUploadDb.getUserId(), userId))
            {
                throw new BusinessException("没有权限修改");
            }
            Short status = videoInfoUploadDb.getStatus();
            /*
             * 我们的视频处理逻辑是只有审核后才能修改视频信息和文件，否则就只能等。
             */
            // 不能修改正在转码或未审核的视频信息
            if (status.equals(VideoStatus.TRANSCODING.getStatus()) || status.equals(VideoStatus.PENDING_REVIEW.getStatus()))
            {
                throw new BusinessException("现在不能提交");
            }

            /* 处理部分 */
            VideoInfoFileUploadQuery videoFileUploadQuery = new VideoInfoFileUploadQuery();
            videoFileUploadQuery.setVideoId(videoId);
            videoFileUploadQuery.setUserId(userId); // 限制住只能改本用户id的视频，因为是通过token获得用户id，可以避免用户改别人视频
            List <VideoInfoFileUpload> dbUploadFileList = videoInfoFileUploadMapper.selectList(videoFileUploadQuery);
            Map <Long, VideoInfoFileUpload> uploadFileMap = uploadFileList.stream()
                                                                          .collect(Collectors.toMap(VideoInfoFileUpload::getUploadId,
                                                                                                    Function.identity(),
                                                                                                    (data1, data2) -> data2));
            boolean isChangedName = false;
            // 新增的视频文件
            ArrayList <VideoInfoFileUpload> newFileList;
            // 被删除的视频文件
            ArrayList <VideoInfoFileUpload> removedFileList = new ArrayList <>();
            for (VideoInfoFileUpload dbFile : dbUploadFileList)
            {
                VideoInfoFileUpload duplicateFile = uploadFileMap.get(dbFile.getUploadId());
                if (duplicateFile == null)
                {
                    removedFileList.add(dbFile);
                }
                else if (!dbFile.getFileName().equals(duplicateFile.getFileName()))
                {
                    isChangedName = true;
                }
            }
            // 只保留了fileId为null的数据，应该是只有保存的videoFileUpload才有fileId
            newFileList = new ArrayList <>(uploadFileList.stream().filter(file -> file.getFileId() == null).toList());

            videoInfoUpload.setLastUpdateTime(curDate);

            boolean isUpdated = !isSameVideoInfoUpload(videoInfoUpload);
            // 修改视频状态
            if (!newFileList.isEmpty())
            {
                videoInfoUpload.setStatus(VideoStatus.TRANSCODING.getStatus());
            }
            else if (isChangedName || isUpdated)
            {
                videoInfoUpload.setStatus(VideoStatus.PENDING_REVIEW.getStatus());
            }
            // 更新视频状态
            videoInfoUploadMapper.updateByVideoId(videoInfoUpload, videoId);

            // 删除用户想删除的视频文件
            if (!removedFileList.isEmpty())
            {
                List <Long> fileIdList = removedFileList.stream().map(VideoInfoFileUpload::getFileId).toList();
                // 数据库层面删除
                videoInfoFileUploadMapper.deleteBatchByFileId(fileIdList, userId);
                List <String> filePathList = removedFileList.stream().map(VideoInfoFileUpload::getFilePath).toList();
                // 删除磁盘上的文件
                mqRepository.addVideoFile2DeleteQueue(filePathList);
            }

            // 更新视频文件记录
            int index = 1;
            for (VideoInfoFileUpload uploadFile : uploadFileList)
            {
                uploadFile.setFileIndex(index++);
                uploadFile.setVideoId(videoId);
                uploadFile.setUserId(videoInfoUpload.getUserId());
                if (uploadFile.getFileId() == null)
                {
                    uploadFile.setFileId(snowflake.nextId());
                    uploadFile.setUpdateType((short) 1);
                    uploadFile.setTransferResult(VideoFileStatus.TRANSCODING.getStatus());
                }
            }
            videoInfoFileUploadMapper.insertOrUpdateBatch(uploadFileList);

            if (!newFileList.isEmpty())
            {
                for (VideoInfoFileUpload newFile : newFileList)
                {
                    newFile.setUserId(userId);
                    newFile.setVideoId(videoId);
                }
                mqRepository.addVideoFile2TranscodingQueue(newFileList);
            }
        }
    }

    /**
     * 查询视频
     *
     * @return 返回一个列表，审核成功的结果会有video_info的字段值
     */
    public List <VideoInfoUploadJoinDTO> loadVideoList(TokenUserInfo tokenUserInfo,
                                                       Short status,
                                                       Integer pageNo,
                                                       Integer pageSize,
                                                       String nameFuzzy)
    {
        VideoInfoUploadQuery query = new VideoInfoUploadQuery();
        query.setUserId(tokenUserInfo.getUserId());
        query.setVideoNameFuzzy(nameFuzzy);
        query.setOrderBy("v.create_time desc");
        if (status != null)
        {
            // 若为-1，则查询未审核的视频，即状态为0、1、2的视频
            if (status == (short) -1)
            {
                query.setExclusiveStatusList(List.of(VideoStatus.REVIEW_SUCCESS.getStatus(), VideoStatus.REVIEW_FAILED.getStatus()));
            }
            else // 否则，查询相应状态的视频
            {
                if (status == (short) 3 || status == (short) 4) query.setStatus(status);
                // 如果不是这两种状态，就查询所有状态的视频
            }
        }

        // 分页查询
        Integer count = videoInfoUploadMapper.selectCount(query);
        query.setPageCalculator(new PageCalculator(pageNo, count, pageSize));
        return videoInfoUploadMapper.selectListWithVideoInfo(query);
    }

    /**
     * 获取不同状态视频的数量
     *
     * @return 三种状态的视频数量
     */
    public VideoStatusCountVO getVideoStatusCount(TokenUserInfo tokenUserInfo)
    {
        Long userId = tokenUserInfo.getUserId();
        VideoInfoUploadQuery query = new VideoInfoUploadQuery();
        query.setUserId(userId);
        // 查找审核通过视频
        query.setStatus(VideoStatus.REVIEW_SUCCESS.getStatus());
        Integer successCount = videoInfoUploadMapper.selectCount(query);
        // 查找审核不通过视频
        query.setStatus(VideoStatus.REVIEW_FAILED.getStatus());
        Integer failedCount = videoInfoUploadMapper.selectCount(query);
        // 查找待审核视频
        query.setStatus(null);
        query.setExclusiveStatusList(List.of(VideoStatus.REVIEW_SUCCESS.getStatus(), VideoStatus.REVIEW_FAILED.getStatus()));
        Integer pendingCount = videoInfoUploadMapper.selectCount(query);
        return new VideoStatusCountVO(pendingCount, successCount, failedCount);
    }

    /**
     * 检查视频信息是否相同<hr/>
     * 具体检查：标题、封面、标签、简介、互动设置
     *
     * @param newInfo 用户提交的视频信息
     * @return 若相同，返回true，否则返回false
     */
    private boolean isSameVideoInfoUpload(VideoInfoUpload newInfo)
    {
        VideoInfoUpload dbInfo = videoInfoUploadMapper.selectByVideoId(newInfo.getVideoId());
        return newInfo.getVideoName().equals(dbInfo.getVideoName()) && newInfo.getVideoCover()
                                                                              .equals(dbInfo.getVideoCover()) && newInfo.getTags()
                                                                                                                        .equals(dbInfo.getTags()) && newInfo.getIntroduction()
                                                                                                                                                            .equals(dbInfo.getIntroduction()) && newInfo.getInteraction()
                                                                                                                                                                                                        .equals(dbInfo.getInteraction());
    }


}
