package com.neon.niloweb.service;

import cn.hutool.core.lang.Snowflake;
import com.esotericsoftware.minlog.Log;
import com.neon.nilocommon.entity.constants.MqInfo;
import com.neon.nilocommon.entity.dto.TokenUserInfo;
import com.neon.nilocommon.entity.enums.VideoFileStatus;
import com.neon.nilocommon.entity.enums.VideoStatus;
import com.neon.nilocommon.entity.po.VideoInfoFileUpload;
import com.neon.nilocommon.entity.po.VideoInfoUpload;
import com.neon.nilocommon.entity.query.VideoInfoFileUploadQuery;
import com.neon.nilocommon.entity.query.VideoInfoUploadQuery;
import com.neon.nilocommon.exception.BusinessException;
import com.neon.niloweb.config.SystemConfig;
import com.neon.niloweb.mapper.VideoInfoFileUploadMapper;
import com.neon.niloweb.mapper.VideoInfoUploadMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Service
public class CreativeCenterVideoUploadService
{
    private final RabbitTemplate rabbitTemplate;

    private final SystemConfig systemConfig;

    private final VideoInfoUploadMapper <VideoInfoUpload, VideoInfoUploadQuery> videoInfoUploadMapper;

    private final VideoInfoFileUploadMapper <VideoInfoFileUpload, VideoInfoFileUploadQuery> videoInfoFileUploadMapper;

    private final Snowflake snowflake;

    @Value("${project.folder}")
    private String rootPath;


    /**
     * 视频上传<hr/>
     * 视频上传流程：<br/>
     * <li> 1. 先将字段整合成videoInfoUpload类 </li>
     * <li> 2. 检验分P数是否在合适范围内 </li>
     * <li> 3. 分支，如果是是新视频，就将视频信息记录和视频文件记录保存在mysql中，并将视频文件全部交给MQ转码 </li>
     * <li> 4. 分支，如果是提交过的视频做修改，如果这个视频没有转码完成或审核完成，就不能继续修改。如果可以修改，那么会上传新出现的视频文件，删除未出现的视频文件。如果视频文件相同，那么就修改一下序号。 </li>
     */
    @Transactional(rollbackFor = Exception.class)
    public void videoUpload(Long videoId,
                            String coverPath,
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
        // 将传入的参数赋值给视频信息对象
        VideoInfoUpload videoInfoUpload = new VideoInfoUpload();
        videoInfoUpload.setVideoId(videoId);
        videoInfoUpload.setVideoCover(coverPath);
        videoInfoUpload.setVideoName(videoTitle);
        videoInfoUpload.setUserId(tokenUserInfo.getUserId());
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
            addVideoFile2TranscodingQueue(uploadFileList);
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
            videoFileUploadQuery.setUserId(tokenUserInfo.getUserId()); // 限制住只能改本用户id的视频，因为是通过token获得用户id，可以避免用户改别人视频
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
                List <Long> fileIdList = removedFileList.stream().map(VideoInfoFileUpload::getUploadId).toList();
                // 数据库层面删除
                videoInfoFileUploadMapper.deleteBatchByFileId(fileIdList, tokenUserInfo.getUserId());
                List <String> filePathList = removedFileList.stream().map(VideoInfoFileUpload::getFilePath).toList();
                // 删除磁盘上的文件
                addVideoFile2DeleteQueue(filePathList);
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
                    newFile.setUserId(tokenUserInfo.getUserId());
                    newFile.setVideoId(videoId);
                }
                addVideoFile2TranscodingQueue(newFileList);
            }
        }
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

    /**
     * 将视频文件的删除任务添加至MQ <hr/>
     * 将List拆分为单个路径，一个路径对应一个message传给MQ<br/>
     * 因为这个操作的性能瓶颈在磁盘删除操作，所以即使拆分为单个路径这个性能损失也不算严重（就目前而言）
     *
     * @param filePathList 一个列表，元素为要删除的文件路径
     */
    private void addVideoFile2DeleteQueue(List <String> filePathList)
    {
        for (String path : filePathList)
        {
            rabbitTemplate.convertAndSend(MqInfo.STORAGE_EXCHANGE, MqInfo.STORAGE_DELETE_ROUTING_KEY, path);
        }
    }

    /**
     * 将视频文件的转码任务添加至MQ <hr/>
     * 将List拆分为单个路径，一个路径对应一个message传给MQ<br/>
     *
     * @param fileUploadList 一个列表，元素为要转码的视频文件的bean
     */
    private void addVideoFile2TranscodingQueue(List <VideoInfoFileUpload> fileUploadList)
    {
        for (VideoInfoFileUpload fileUpload : fileUploadList)
        {
            rabbitTemplate.convertAndSend(MqInfo.STORAGE_EXCHANGE, MqInfo.STORAGE_TRANSCODING_ROUTING_KEY, fileUpload);
        }
    }
}
