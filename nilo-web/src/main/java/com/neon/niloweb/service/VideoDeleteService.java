package com.neon.niloweb.service;

import com.neon.nilocommon.entity.constants.MinioKey;
import com.neon.nilocommon.entity.dto.comment.CommentArchiveDTO;
import com.neon.nilocommon.entity.dto.mq.VideoDeleteDTO;
import com.neon.nilocommon.entity.enums.ResponseCode;
import com.neon.nilocommon.entity.enums.comment.OperationType;
import com.neon.nilocommon.entity.enums.videoInfoArchive.DeleterType;
import com.neon.nilocommon.entity.enums.videoInfoUpload.VideoStatus;
import com.neon.nilocommon.entity.po.*;
import com.neon.nilocommon.entity.query.*;
import com.neon.nilocommon.entity.vo.ResponseVO;
import com.neon.nilocommon.repository.redis.SystemConfigRedisRepository;
import com.neon.nilocommon.util.FileUtil;
import com.neon.niloweb.feign.storage.InnerImageFeignClient;
import com.neon.niloweb.feign.storage.InnerVideoFileFeignClient;
import com.neon.niloweb.mapper.*;
import com.neon.niloweb.repository.rabbitmq.CommentMqRepository;
import com.neon.niloweb.repository.rabbitmq.ImageMqRepository;
import com.neon.niloweb.repository.rabbitmq.VideoMqRepository;
import com.neon.niloweb.repository.redis.AccountRedisRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Supplier;

/**
 * 删除视频（MQ 消费时执行）<hr/>
 * <p>用户在创作中心删除、管理员在后台删除，请求端只做校验，然后发 {@link VideoDeleteDTO}，
 * 由 nilo-mq-consumer 调内部接口进到这里。归档、移动文件这些耗时操作都在这里做，删除接口不用等。</p>
 * <p>是否已发布按当时的数据重新判断：请求和处理之间视频可能已经被审核通过、或者已经被删过。
 * 校验不通过只记日志返回，不抛业务异常，否则消息会被反复重试再进死信队列。</p>
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class VideoDeleteService
{
    private final UserInfoMapper <UserInfo, UserInfoQuery> userInfoMapper;

    private final MediaOwnershipMapper <MediaOwnership, MediaOwnershipQuery> mediaOwnershipMapper;

    // ----- upload -----

    private final VideoInfoUploadMapper <VideoInfoUpload, VideoInfoUploadQuery> videoInfoUploadMapper;

    private final VideoInfoFileUploadMapper <VideoInfoFileUpload, VideoInfoFileUploadQuery> videoInfoFileUploadMapper;

    // ----- info -----

    private final VideoInfoMapper <VideoInfo, VideoInfoQuery> videoInfoMapper;

    private final VideoInfoFileMapper <VideoInfoFile, VideoInfoFileQuery> videoInfoFileMapper;

    private final VideoDanmakuMapper <VideoDanmaku, VideoDanmakuQuery> videoDanmakuMapper;

    private final UserVideoActionMapper <UserVideoAction, UserVideoActionQuery> userVideoActionMapper;

    // ----- archive -----

    private final VideoInfoArchiveMapper <VideoInfoArchive, VideoInfoArchiveQuery> videoInfoArchiveMapper;

    private final VideoInfoFileArchiveMapper <VideoInfoFileArchive, VideoInfoFileArchiveQuery> videoInfoFileArchiveMapper;

    private final VideoDanmakuArchiveMapper <VideoDanmakuArchive, VideoDanmakuArchiveQuery> videoDanmakuArchiveMapper;

    private final UserVideoActionArchiveMapper <UserVideoActionArchive, UserVideoActionArchiveQuery> userVideoActionArchiveMapper;

    // ----- Redis repository -----

    private final AccountRedisRepository accountRedisRepository;

    private final SystemConfigRedisRepository systemConfigRedisRepository;

    // ----- other -----

    private final VideoMqRepository videoMqRepository;

    private final ImageMqRepository imageMqRepository;

    private final CommentMqRepository commentMqRepository;

    private final InnerImageFeignClient innerImageFeignClient;

    private final InnerVideoFileFeignClient innerVideoFileFeignClient;

    /**
     * 删除视频：没发布的直接删除，不可恢复；已发布的归档，文件移回 PENDING，别人就看不到了
     *
     * @param dto 删除任务
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteVideo(VideoDeleteDTO dto)
    {
        long userId = dto.getUserId();
        long videoId = dto.getVideoId();

        VideoInfo videoInfo = videoInfoMapper.selectByVideoId(videoId);

        // 正式表没有记录，就是还没发布的视频
        if (videoInfo == null)
        {
            // 限定用户ID查一下记录
            VideoInfoUpload videoInfoUpload = videoInfoUploadMapper.selectByVideoIdAndUserId(videoId, userId);
            if (videoInfoUpload == null)
            {
                log.warn("无法找到视频，跳过, videoId={}, userId={}", videoId, userId);
                return;
            }

            // 只能删除 转码失败/待审核/审核失败 的视频
            Short status = videoInfoUpload.getStatus();
            if (!(VideoStatus.TRANSCODING_FAIL.getStatus().equals(status) || VideoStatus.PENDING_REVIEW.getStatus()
                                                                                                       .equals(status) || VideoStatus.REVIEW_FAILED.getStatus()
                                                                                                                                                   .equals(status)))
            {
                log.warn("视频当前状态不允许删除，跳过, videoId={}, status={}", videoId, videoInfoUpload.getStatus());
                return;
            }

            deleteUnpublishedVideo(videoId, videoInfoUpload);
        }
        else
        {
            // 视频不属于该用户
            if (!Objects.equals(videoInfo.getUserId(), userId))
            {
                log.warn("要删除的视频不属于该用户，跳过, videoId={}, userId={}", videoId, userId);
                return;
            }

            // 已经归档过，说明这条删除已经处理过（重复投递）
            if (videoInfoArchiveMapper.selectByVideoId(videoId) != null)
            {
                log.warn("视频已经归档，跳过, videoId={}", videoId);
                return;
            }

            deletePublishedVideo(userId, videoId, dto.getDeleterType(), dto.getDetail(), videoInfo);
        }
    }

    /**
     * 删除未发布的视频<hr/>
     * 删除上传记录和从属关系，事务提交后把文件交给删除队列
     */
    private void deleteUnpublishedVideo(long videoId, VideoInfoUpload videoInfoUpload)
    {
        // 构建视频文件删除路径列表
        List <VideoInfoFileUpload> uploadFileList = videoInfoFileUploadMapper.selectByVideoId(videoId);
        List <String> deletePathList;
        if (uploadFileList == null || uploadFileList.isEmpty())
        {
            deletePathList = List.of();
        }
        else
        {
            deletePathList = uploadFileList.stream()
                                           .map(VideoInfoFileUpload::getFilePath)
                                           .filter(filePath -> filePath != null && !filePath.isBlank())
                                           .distinct()
                                           .toList();
        }

        // 查出图片路径
        String coverKey = videoInfoUpload.getVideoCover();

        ArrayList <String> deleteKeys = new ArrayList <>();
        // 添加视频
        if (!deletePathList.isEmpty())
        {
            deleteKeys.addAll(deletePathList);
        }
        // 添加图片和缩略图
        if (coverKey != null && !coverKey.isBlank())
        {
            deleteKeys.add(coverKey);
            deleteKeys.add(FileUtil.constructThumbnailName(coverKey));
        }

        // 删除文件记录和视频信息记录
        videoInfoFileUploadMapper.deleteByVideoId(videoId);
        videoInfoUploadMapper.deleteByVideoId(videoId);

        // 删除从属关系
        mediaOwnershipMapper.deleteBatchByObjectKey(deleteKeys);


        // --- 在事务提交后把删除路径交给MQ ---

        // 删除视频文件
        if (!deletePathList.isEmpty())
        {
            addVideoFilesToVideoDeleteQueueAfterCommit(deletePathList);
        }

        // 构建图片文件删除路径列表
        // 删除封面文件
        if (coverKey != null && !coverKey.isBlank())
        {
            addImageFilesToImageDeleteQueueAfterCommit(List.of(coverKey, FileUtil.constructThumbnailName(coverKey)));
        }
    }

    /**
     * 删除已发布的视频<hr/>
     * 扣回发布奖励的硬币，数据迁移到归档表，封面和视频文件移回 PENDING
     */
    private void deletePublishedVideo(long userId, long videoId, DeleterType deleterType, String detail, VideoInfo videoInfo)
    {
        // 给用户扣除发布视频时获得的硬币
        userInfoMapper.decreaseCoinForVideoDelete(userId, systemConfigRedisRepository.getSystemConfig().getRewardsPreUpload());

        // 迁移之前把改查的记录查出来
        List <VideoInfoFile> videoInfoFiles = videoInfoFileMapper.selectByVideoId(videoId);

        // 数据迁移和删除
        archiveVideo(userId, videoId, deleterType, detail, videoInfo);

        // 移动封面和缩略图
        moveImage(MinioKey.PUBLIC_PREFIX, MinioKey.PENDING_PREFIX, videoInfo.getVideoCover());

        // 移动视频文件
        List <String> videoBaseKeyList = videoInfoFiles.stream().map(VideoInfoFile::getFilePath).toList();
        moveVideoFiles(MinioKey.PUBLIC_PREFIX, MinioKey.PENDING_PREFIX, videoBaseKeyList);
    }

    /**
     * 视频归档
     *
     * @param userId      用户ID
     * @param videoId     视频ID
     * @param deleterType 删除者类型
     * @param detail      详细信息
     * @param videoInfo   视频信息
     */
    private void archiveVideo(long userId, long videoId, DeleterType deleterType, String detail, VideoInfo videoInfo)
    {
        // --- 将所有数据迁移到 archive 表 ---

        // video_info
        // 我们认为 video_info 代表了删除的操作，因此其他表如果存在原来的记录，将不会认为是重复删除，并会将archive中对应的记录清除并重新录入
        // 是否重复删除已经在 deleteVideo 里检查过了
        VideoInfoArchive videoInfoArchive = new VideoInfoArchive();
        videoInfoArchive.setDeleteTime(LocalDateTime.now());
        videoInfoArchive.setDeleterType(deleterType.getValue());
        videoInfoArchive.setDeleteDetail(detail);
        BeanUtils.copyProperties(videoInfo, videoInfoArchive);
        videoInfoArchiveMapper.insert(videoInfoArchive);

        // video_info_file
        VideoInfoFileQuery videoInfoFileQuery = new VideoInfoFileQuery();
        videoInfoFileQuery.setVideoId(videoId);
        List <VideoInfoFile> videoInfoFileList = videoInfoFileMapper.selectList(videoInfoFileQuery);
        VideoInfoFileArchiveQuery videoInfoFileArchiveQuery = new VideoInfoFileArchiveQuery();
        videoInfoFileArchiveQuery.setVideoId(videoId);
        videoInfoFileArchiveMapper.deleteByParam(videoInfoFileArchiveQuery);
        archiveBatch(videoInfoFileList, VideoInfoFileArchive::new, videoInfoFileArchiveMapper);

        // video_comment + user_comment_action：事务提交后异步通知评论服务归档，保证最终一致性
        sendCommentArchiveOperationAfterCommit(new CommentArchiveDTO(videoId, OperationType.ARCHIVE));

        // video_danmaku
        VideoDanmakuQuery videoDanmakuQuery = new VideoDanmakuQuery();
        videoDanmakuQuery.setVideoId(videoId);
        List <VideoDanmaku> videoDanmakuList = videoDanmakuMapper.selectList(videoDanmakuQuery);
        VideoDanmakuArchiveQuery videoDanmakuArchiveQuery = new VideoDanmakuArchiveQuery();
        videoDanmakuArchiveQuery.setVideoId(videoId);
        videoDanmakuArchiveMapper.deleteByParam(videoDanmakuArchiveQuery);
        archiveBatch(videoDanmakuList, VideoDanmakuArchive::new, videoDanmakuArchiveMapper);

        // user_video_action
        UserVideoActionQuery userVideoActionQuery = new UserVideoActionQuery();
        userVideoActionQuery.setVideoId(videoId);
        List <UserVideoAction> userVideoActionList = userVideoActionMapper.selectList(userVideoActionQuery);
        UserVideoActionArchiveQuery userVideoActionArchiveQuery = new UserVideoActionArchiveQuery();
        userVideoActionArchiveQuery.setVideoId(videoId);
        userVideoActionArchiveMapper.deleteByParam(userVideoActionArchiveQuery);
        archiveBatch(userVideoActionList, UserVideoActionArchive::new, userVideoActionArchiveMapper);

        // --- 删除原业务表数据 ---

        userVideoActionMapper.deleteByParam(userVideoActionQuery);
        videoDanmakuMapper.deleteByParam(videoDanmakuQuery);
        videoInfoFileMapper.deleteByParam(videoInfoFileQuery);
        videoInfoMapper.deleteByVideoId(videoId);

        // --- 清理 upload 表 ---

        VideoInfoFileUploadQuery videoInfoFileUploadQuery = new VideoInfoFileUploadQuery();
        videoInfoFileUploadQuery.setVideoId(videoId);
        videoInfoFileUploadMapper.deleteByParam(videoInfoFileUploadQuery);

        VideoInfoUploadQuery videoInfoUploadQuery = new VideoInfoUploadQuery();
        videoInfoUploadQuery.setVideoId(videoId);
        videoInfoUploadMapper.deleteByParam(videoInfoUploadQuery);

        // 修改用户硬币
        accountRedisRepository.deleteUserState(userId);
    }

    /**
     * 批量将数据从一张表导入到另一张表
     *
     * @param sourceList     原始数据列表
     * @param targetSupplier 构建单个目标数据PO的函数，应为 Supplier
     * @param archiveMapper  目标表 mapper
     * @param <S>            原始数据PO类型
     * @param <T>            目标数据PO类型
     */
    private <S, T> void archiveBatch(List <S> sourceList, Supplier <T> targetSupplier, BaseMapper <T, ?> archiveMapper)
    {
        if (sourceList == null || sourceList.isEmpty())
        {
            return;
        }
        List <T> archiveList = sourceList.stream().map(source ->
                                                       {
                                                           T target = targetSupplier.get();
                                                           BeanUtils.copyProperties(source, target);
                                                           return target;
                                                       }).toList();
        archiveMapper.insertBatch(archiveList);
    }

    /**
     * 移动图片（封面+缩略图）
     *
     * @param sourcePrefix 源前缀
     * @param targetPrefix 目标前缀
     * @param coverBaseKey 封面的 baseKey
     */
    private void moveImage(String sourcePrefix, String targetPrefix, String coverBaseKey)
    {
        String thumbnailKey = FileUtil.constructThumbnailName(coverBaseKey);
        Map <String, String> imageKeyMap = Map.of(sourcePrefix + coverBaseKey,
                                                  targetPrefix + coverBaseKey,
                                                  sourcePrefix + thumbnailKey,
                                                  targetPrefix + thumbnailKey);
        ResponseVO <Void> imageResult = innerImageFeignClient.batchMove(imageKeyMap);
        if (!ResponseCode.SUCCESS.getCode().equals(imageResult.getCode()))
        {
            throw new RuntimeException("封面移动失败");
        }
    }

    /**
     * 移动视频文件
     *
     * @param sourcePrefix     源前缀
     * @param targetPrefix     目标前缀
     * @param videoBaseKeyList 视频文件 baseKey 列表
     */
    private void moveVideoFiles(String sourcePrefix, String targetPrefix, List <String> videoBaseKeyList)
    {
        if (videoBaseKeyList == null || videoBaseKeyList.isEmpty())
        {
            return;
        }
        Map <String, String> videoDirectoryMap = new LinkedHashMap <>();
        for (String baseKey : videoBaseKeyList)
        {
            videoDirectoryMap.put(sourcePrefix + baseKey, targetPrefix + baseKey);
        }
        ResponseVO <Void> videoResult = innerVideoFileFeignClient.batchMoveDirectory(videoDirectoryMap);
        if (!ResponseCode.SUCCESS.getCode().equals(videoResult.getCode()))
        {
            throw new RuntimeException("视频文件移动失败");
        }
    }

    /**
     * 在事务提交后把文件路径交给图片删除队列
     *
     * @param filePathList 文件路径列表
     */
    private void addImageFilesToImageDeleteQueueAfterCommit(List <String> filePathList)
    {
        if (filePathList == null || filePathList.isEmpty())
        {
            return;
        }

        if (!TransactionSynchronizationManager.isSynchronizationActive())
        {
            imageMqRepository.addImageFilesToDeleteQueue(filePathList);
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization()
        {
            @Override
            public void afterCommit()
            {
                imageMqRepository.addImageFilesToDeleteQueue(filePathList);
            }
        });
    }

    /**
     * 在事务提交后把文件路径交给删除队列
     *
     * @param filePathList 文件路径列表
     */
    private void addVideoFilesToVideoDeleteQueueAfterCommit(List <String> filePathList)
    {
        if (filePathList == null || filePathList.isEmpty())
        {
            return;
        }

        if (!TransactionSynchronizationManager.isSynchronizationActive())
        {
            videoMqRepository.addVideoFilesToVideoDeleteQueue(filePathList);
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization()
        {
            @Override
            public void afterCommit()
            {
                videoMqRepository.addVideoFilesToVideoDeleteQueue(filePathList);
            }
        });
    }

    /**
     * 在事务提交后发送视频评论归档/恢复/彻底删除消息<hr/>
     * <p>本地事务未提交成功时绝不会通知评论服务，避免出现"本地回滚但评论已归档"的不一致状态</p>
     */
    private void sendCommentArchiveOperationAfterCommit(CommentArchiveDTO dto)
    {
        if (dto == null)
        {
            return;
        }

        if (!TransactionSynchronizationManager.isSynchronizationActive())
        {
            commentMqRepository.sendCommentArchiveOperation(dto);
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization()
        {
            @Override
            public void afterCommit()
            {
                commentMqRepository.sendCommentArchiveOperation(dto);
            }
        });
    }
}
