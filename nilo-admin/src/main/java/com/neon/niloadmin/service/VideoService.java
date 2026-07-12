package com.neon.niloadmin.service;

import com.neon.niloadmin.config.AdminConfig;
import com.neon.niloadmin.feign.storage.ImageFeignClient;
import com.neon.niloadmin.feign.storage.VideoFileFeignClient;
import com.neon.niloadmin.mapper.*;
import com.neon.niloadmin.repository.rabbitmq.MqRepository;
import com.neon.niloadmin.repository.redis.AccountRedisRepository;
import com.neon.nilocommon.entity.constants.MinioKey;
import com.neon.nilocommon.entity.dto.VideoInfoUploadAdminJoinDTO;
import com.neon.nilocommon.entity.enums.ResponseCode;
import com.neon.nilocommon.entity.enums.videoInfo.RecommendType;
import com.neon.nilocommon.entity.enums.videoInfoArchive.DeleterType;
import com.neon.nilocommon.entity.enums.videoInfoFileUpload.UpdateType;
import com.neon.nilocommon.entity.enums.videoInfoFileUpload.VideoFileStatus;
import com.neon.nilocommon.entity.enums.videoInfoUpload.VideoStatus;
import com.neon.nilocommon.entity.po.*;
import com.neon.nilocommon.entity.query.*;
import com.neon.nilocommon.entity.vo.ResponseVO;
import com.neon.nilocommon.exception.BusinessException;
import com.neon.nilocommon.repository.redis.SystemConfigRedisRepository;
import com.neon.nilocommon.util.FileUtil;
import com.neon.nilocommon.util.PageCalculator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
@Service
public class VideoService
{
    /* Service */

    private final UserMessageService userMessageService;

    /* Repository */

    private final UserInfoMapper <UserInfo, UserInfoQuery> userInfoMapper;

    // --- upload ---

    private final VideoInfoUploadMapper <VideoInfoUpload, VideoInfoUploadQuery> videoInfoUploadMapper;

    private final VideoInfoFileUploadMapper <VideoInfoFileUpload, VideoInfoFileUploadQuery> videoInfoFileUploadMapper;

    private final MediaOwnershipMapper <MediaOwnership, MediaOwnershipQuery> mediaOwnershipMapper;

    // --- info ---

    private final VideoInfoMapper <VideoInfo, VideoInfoQuery> videoInfoMapper;

    private final VideoInfoFileMapper <VideoInfoFile, VideoInfoFileQuery> videoInfoFileMapper;

    private final VideoCommentMapper <VideoComment, VideoCommentQuery> videoCommentMapper;

    private final VideoDanmakuMapper <VideoDanmaku, VideoDanmakuQuery> videoDanmakuMapper;

    private final UserCommentActionMapper <UserCommentAction, UserCommentActionQuery> userCommentActionMapper;

    private final UserVideoActionMapper <UserVideoAction, UserVideoActionQuery> userVideoActionMapper;

    // --- archive ---

    private final VideoInfoArchiveMapper <VideoInfoArchive, VideoInfoArchiveQuery> videoInfoArchiveMapper;

    private final VideoInfoFileArchiveMapper <VideoInfoFileArchive, VideoInfoFileArchiveQuery> videoInfoFileArchiveMapper;

    private final VideoCommentArchiveMapper <VideoCommentArchive, VideoCommentArchiveQuery> videoCommentArchiveMapper;

    private final VideoDanmakuArchiveMapper <VideoDanmakuArchive, VideoDanmakuArchiveQuery> videoDanmakuArchiveMapper;

    private final UserCommentActionArchiveMapper <UserCommentActionArchive, UserCommentActionArchiveQuery> userCommentActionArchiveMapper;

    private final UserVideoActionArchiveMapper <UserVideoActionArchive, UserVideoActionArchiveQuery> userVideoActionArchiveMapper;

    private final AccountRedisRepository accountRedisRepository;

    private final MqRepository mqRepository;

    /* Other */
    private final ImageFeignClient imageFeignClient;

    private final VideoFileFeignClient videoFileFeignClient;

    private final AdminConfig adminConfig;

    /* 常量 */

    private static final String reviewSuccessMessage = "您的视频已经通过审核";
    private final SystemConfigRedisRepository systemConfigRedisRepository;

    /**
     * 查询视频
     *
     * @return 返回一个列表，审核成功的结果会有video_info的字段值
     */
    public List <VideoInfoUploadAdminJoinDTO> loadVideoList(VideoInfoUploadQuery infoUploadQuery,
                                                            Boolean orderByLastUpdateTimeAsc,
                                                            Boolean orderByStatusAsc)
    {
        StringBuilder orderBy = new StringBuilder();

        if (orderByLastUpdateTimeAsc != null)
        {
            orderBy.append("v.last_update_time ").append(orderByLastUpdateTimeAsc ? "asc" : "desc");
        }

        if (orderByStatusAsc != null)
        {
            if (!orderBy.isEmpty()) orderBy.append(", ");
            orderBy.append("v.status ").append(orderByStatusAsc ? "asc" : "desc");
        }

        // 如果都没有，默认按照创建时间排序
        if (orderBy.isEmpty())
        {
            orderBy.append("v.create_time desc");
        }

        infoUploadQuery.setOrderBy(orderBy.toString());

        // 分页查询
        Integer count = videoInfoUploadMapper.selectCount(infoUploadQuery);
        infoUploadQuery.setPageCalculator(new PageCalculator(infoUploadQuery.getPageNo(), count, infoUploadQuery.getPageSize()));
        return videoInfoUploadMapper.selectListWithVideoInfoWithUserInfo(infoUploadQuery);
    }

    public Integer getVideoUploadCount(VideoInfoUploadQuery infoUploadQuery)
    {
        return videoInfoUploadMapper.selectCount(infoUploadQuery);
    }

    /**
     * 查询视频分P信息
     *
     * @param videoId 视频ID
     * @return 分P文件列表
     */
    public List <VideoInfoFileUpload> loadVideoFileList(long videoId)
    {
        return videoInfoFileUploadMapper.selectByVideoId(videoId);
    }

    /**
     * 审核视频
     *
     * @param videoId      视频id
     * @param reviewResult 审核结果
     * @param refuseReason 拒绝原因
     */
    @Transactional(rollbackFor = Exception.class)
    public void reviewVideo(long videoId, boolean reviewResult, String refuseReason)
    {
        // 将上传视频状态设置为审核通过/未通过
        VideoInfoUpload newVideoInfoUpload = new VideoInfoUpload();
        newVideoInfoUpload.setStatus(reviewResult ? VideoStatus.REVIEW_SUCCESS.getStatus() : VideoStatus.REVIEW_FAILED.getStatus()); // 审核状态
        VideoInfoUploadQuery infoUploadQuery = new VideoInfoUploadQuery();
        infoUploadQuery.setStatus(VideoStatus.PENDING_REVIEW.getStatus()); // 保证status为2的视频才能被更新，避免重复更新，防止并发问题
        infoUploadQuery.setVideoId(videoId);
        Integer updateCount = videoInfoUploadMapper.updateByParam(newVideoInfoUpload, infoUploadQuery);
        if (updateCount == 0)
        {
            throw new RuntimeException("审核失败！");
        }

        // 将所有上传文件都设置为没有更新
        VideoInfoFileUpload newFileUpload = new VideoInfoFileUpload();
        newFileUpload.setUpdateType(UpdateType.NO_UPDATE.getUpdateType());
        VideoInfoFileUploadQuery fileUploadQuery = new VideoInfoFileUploadQuery();
        fileUploadQuery.setVideoId(videoId);
        videoInfoFileUploadMapper.updateByParam(newFileUpload, fileUploadQuery);

        // 如果审核不通过
        if (!reviewResult)
        {
            // 先把本次上传文件都查出来
            List <VideoInfoFileUpload> dbFileUploadList = videoInfoFileUploadMapper.selectByVideoId(videoId);

            // 先预置好列表
            List <Long> newUploadFileIdList = new ArrayList <>();
            List <String> deleteBaseKeyList = new ArrayList <>();

            // 从正式表查一下正式表公开的文件资源
            List <VideoInfoFile> publishedFileList = videoInfoFileMapper.selectByVideoId(videoId);
            // 如果正式表记录存在，说明不是第一次上传
            boolean isPublished = publishedFileList != null && !publishedFileList.isEmpty();
            if (isPublished)
            {
                Set <Long> publishedFileIdSet = publishedFileList.stream()
                                                                 .map(VideoInfoFile::getFileId)
                                                                 .filter(Objects::nonNull)
                                                                 .collect(Collectors.toSet());

                // 把所有新上传的文件筛选出来，将fileId和key添加到列表中
                for (VideoInfoFileUpload dbUploadFile : dbFileUploadList)
                {
                    Long fileId = dbUploadFile.getFileId();
                    if (fileId == null)
                    {
                        log.warn("文件ID为空：{}", dbUploadFile);
                        continue;
                    }

                    if (!publishedFileIdSet.contains(fileId))
                    {
                        newUploadFileIdList.add(fileId);
                        String baseKey = dbUploadFile.getFilePath();
                        if (baseKey != null && !baseKey.isBlank())
                        {
                            deleteBaseKeyList.add(baseKey);
                        }
                    }
                }
            }
            // 如果记录不存在，就不必去正式表查了
            // 直接把所有新上传文件删除即可
            else
            {
                newUploadFileIdList.addAll(dbFileUploadList.stream()
                                                           .map(VideoInfoFileUpload::getFileId)
                                                           .filter(Objects::nonNull)
                                                           .toList());
                deleteBaseKeyList.addAll(dbFileUploadList.stream()
                                                         .map(VideoInfoFileUpload::getFilePath)
                                                         .filter(Objects::nonNull)
                                                         .toList());
            }

            // 把key做个去重
            List <String> distinctDeleteBaseKeyList = deleteBaseKeyList.stream().distinct().toList();

            // 删除所有新上传文件的记录
            if (!newUploadFileIdList.isEmpty())
            {
                videoInfoFileUploadMapper.deleteBatchByFileId(newUploadFileIdList);
            }

            // 删除所有新上传文件的 归属记录 以及 保存在minio的文件
            if (!distinctDeleteBaseKeyList.isEmpty())
            {
                mediaOwnershipMapper.deleteBatchByObjectKey(distinctDeleteBaseKeyList);
                mqRepository.addKeysToVideoDeleteQueue(distinctDeleteBaseKeyList);
            }

            // 为了方便用户显示，不删除新提交的封面

            // 如果正式表记录存在，那么就回写所有原有文件记录
            if (isPublished)
            {
                List <VideoInfoFileUpload> rewrittenUploadList = publishedFileList.stream().map(publishedFile ->
                                                                                                {
                                                                                                    VideoInfoFileUpload uploadFile = new VideoInfoFileUpload();
                                                                                                    BeanUtils.copyProperties(
                                                                                                            publishedFile,
                                                                                                            uploadFile);
                                                                                                    uploadFile.setTransferResult(
                                                                                                            VideoFileStatus.TRANSCODING_SUCCESS.getStatus());
                                                                                                    uploadFile.setUpdateType(
                                                                                                            UpdateType.NO_UPDATE.getUpdateType());
                                                                                                    return uploadFile;
                                                                                                }).toList();
                videoInfoFileUploadMapper.insertOrUpdateBatch(rewrittenUploadList);
            }

            // 向用户发送系统消息，通知用户视频审核状态
            CompletableFuture <Void> completableFuture = userMessageService.sendVideoReviewMessage(videoId,
                                                                                                   "您的视频未通过，原因:" + refuseReason);
            CompletableFuture.allOf(completableFuture).exceptionally(e ->
                                                                     {
                                                                         log.warn("发送审核不通过消息失败，异常信息：{}",
                                                                                  e.toString());
                                                                         return null;
                                                                     });
        }
        // 如果审核通过
        else
        {
            VideoInfoUpload infoUpload = videoInfoUploadMapper.selectByVideoId(videoId);

            VideoInfo videoInfo = videoInfoMapper.selectByVideoId(videoId);

            VideoInfo newVideoInfo = new VideoInfo();

            // 先看看有没有发布过
            boolean isFirstPublish = videoInfo == null;

            // 如果是第一次发布
            if (isFirstPublish)
            {
                newVideoInfo = new VideoInfo();

                Long userId = infoUpload.getUserId();
                if (userId == null)
                {
                    throw new BusinessException("数据库记录错误，没有用户ID！");
                }
                userInfoMapper.increaseCoin(userId, systemConfigRedisRepository.getSystemConfig().getRewardsPreUpload());
                // 更新 UserState，保证缓存一致性
                accountRedisRepository.deleteUserState(userId);
            }

            // 把新上传视频信息复制到videoInfo中
            BeanUtils.copyProperties(infoUpload, newVideoInfo);


            // 删除旧的video_info_file记录（DB层面）
            List <VideoInfoFile> oldFileList = videoInfoFileMapper.selectByVideoId(videoId);
            videoInfoFileMapper.deleteByVideoId(videoId);

            // 获取videoInfoFileUpload记录
            List <VideoInfoFileUpload> infoFileUploadList = videoInfoFileUploadMapper.selectByVideoId(videoId);

            // 检查空路径文件记录
            // 空路径可能由于很多种情况产生，这些情况是我们意料之外的，为了防止错误地前端展示，这里删除空路径记录
            List <Long> emptyPathFileIdList = infoFileUploadList.stream()
                                                                .filter(file -> file.getFilePath() == null || file.getFilePath()
                                                                                                                  .isBlank())
                                                                .map(VideoInfoFileUpload::getFileId)
                                                                .filter(Objects::nonNull)
                                                                .toList();

            // 删除空路径文件记录
            if (!emptyPathFileIdList.isEmpty())
            {
                videoInfoFileUploadMapper.deleteBatchByFileId(emptyPathFileIdList);
            }

            // 将获取的记录转化之后插入到video_info_file表
            List <VideoInfoFile> infoFileList = infoFileUploadList.stream()
                                                                  .filter(file -> file.getFilePath() != null && !file.getFilePath()
                                                                                                                     .isBlank()) // 别忘了把刚才的空路径记录过滤了
                                                                  .map(infoFileUpload ->
                                                                       {
                                                                           VideoInfoFile infoFile = new VideoInfoFile();
                                                                           BeanUtils.copyProperties(infoFileUpload, infoFile);
                                                                           return infoFile;
                                                                       })
                                                                  .toList();

            // 一个预防条件，如果审核通过发现并没有实际的视频文件，就抛出异常
            if (infoFileList.isEmpty())
            {
                throw new BusinessException("没有可发布的视频文件");
            }

            videoInfoFileMapper.insertBatch(infoFileList);

            // 删除不需要的视频（所有fileId不存在于新出现的文件中的视频文件）
            List <Long> remainFileIdList = infoFileList.stream().map(VideoInfoFile::getFileId).toList();

            // 过滤出不在新文件列表中的旧文件
            List <VideoInfoFile> deleteFileList = oldFileList.stream()
                                                             .filter(oldFile -> !remainFileIdList.contains(oldFile.getFileId()))
                                                             .toList();

            // 删除文件路径（不带前缀的 baseKey，供删除队列使用）
            List <String> deleteBaseKeyList = deleteFileList.stream()
                                                            .map(VideoInfoFile::getFilePath)
                                                            .filter(filePath -> filePath != null && !filePath.isBlank())
                                                            .distinct()
                                                            .toList();

            // 如果有被删除的旧文件，统一处理
            if (!isFirstPublish && !deleteFileList.isEmpty())
            {
                // 删除旧的视频文件的弹幕
                Integer deletedDanmakuCount = videoDanmakuMapper.deleteByFileIdBatch(deleteFileList.stream()
                                                                                                   .map(VideoInfoFile::getFileId)
                                                                                                   .toList());
                // 减少videoInfo弹幕数，因为我们删除了旧视频文件的弹幕
                if (deletedDanmakuCount != null && deletedDanmakuCount > 0)
                {
                    newVideoInfo.setDanmakuCount(Math.max((videoInfo.getDanmakuCount() == null ? 0 : videoInfo.getDanmakuCount()) - deletedDanmakuCount,
                                                          0));
                }
            }

            // 更新/填入 mysql 数据（ES 由 Canal 同步）
            videoInfoMapper.insertOrUpdate(newVideoInfo);

            // 检查新封面和旧封面状态
            String oldCoverKey = isFirstPublish ? null : videoInfo.getVideoCover();
            String coverKey = infoUpload.getVideoCover();
            boolean coverChanged = !Objects.equals(oldCoverKey, infoUpload.getVideoCover());

            // --- 把从属表信息删除 ---

            List <String> deleteOwnershipObjectKeyList = new ArrayList <>();
            // 检查旧封面要不要删
            if (coverChanged && oldCoverKey != null && !oldCoverKey.isBlank())
            {
                deleteOwnershipObjectKeyList.add(oldCoverKey);
                deleteOwnershipObjectKeyList.add(FileUtil.constructThumbnailName(oldCoverKey));
            }
            // 看看有没有旧视频，如果有也要删
            if (!deleteBaseKeyList.isEmpty())
            {
                deleteOwnershipObjectKeyList.addAll(deleteBaseKeyList);
            }
            // 如果有能删除的从属表信息，统一删除
            if (!deleteOwnershipObjectKeyList.isEmpty())
            {
                mediaOwnershipMapper.deleteBatchByObjectKey(deleteOwnershipObjectKeyList.stream().distinct().toList());
            }

            // --- 文件移动 ---

            // 若封面改变，把新封面移动
            if (coverKey != null && !coverKey.isBlank() && coverChanged)
            {
                moveImage(MinioKey.PENDING_PREFIX, MinioKey.PUBLIC_PREFIX, coverKey);
            }

            // 如果有新文件，把新文件全部移动
            List <String> videoBaseKeyList = infoFileList.stream()
                                                         .map(VideoInfoFile::getFilePath)
                                                         .filter(filePath -> filePath != null && !filePath.isBlank())
                                                         .distinct()
                                                         .toList();
            if (!videoBaseKeyList.isEmpty())
            {
                moveVideoFiles(MinioKey.PENDING_PREFIX, MinioKey.PUBLIC_PREFIX, videoBaseKeyList);
            }

            // --- 文件删除 ---

            // 如果有需要删除的文件，将其在事务提交后加入到删除队列

            if (!deleteBaseKeyList.isEmpty())
            {
                addKeysToVideoDeleteQueueAfterCommit(deleteBaseKeyList);
            }

            // 如果封面有变动，也在事务提交后加入删除队列

            if (coverChanged && oldCoverKey != null && !oldCoverKey.isBlank())
            {
                addKeysToImageDeleteQueueAfterCommit(List.of(oldCoverKey, FileUtil.constructThumbnailName(oldCoverKey)));
            }

            // --- 通知 ---

            // 异步向用户发送系统消息，通知用户视频审核状态
            CompletableFuture <Void> completableFuture = userMessageService.sendVideoReviewMessage(videoId, reviewSuccessMessage);

            CompletableFuture.allOf(completableFuture).exceptionally(e ->
                                                                     {
                                                                         log.warn("发送审核通过消息失败，异常信息：{}",
                                                                                  e.toString());
                                                                         return null;
                                                                     });
        }
    }

    /**
     * 恢复一条视频
     *
     * @param videoId 视频ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void recoverVideo(long videoId)
    {
        VideoInfoArchive videoInfoArchive = videoInfoArchiveMapper.selectByVideoId(videoId);
        // 如果视频不在存档中
        if (videoInfoArchive == null)
        {
            throw new BusinessException("没发现该视频");
        }

        // 恢复用户硬币
        userInfoMapper.increaseCoin(videoInfoArchive.getUserId(),
                                    systemConfigRedisRepository.getSystemConfig().getRewardsPreUpload());

        // 恢复视频信息，从存档中读取数据
        VideoInfo videoInfo = loadVideoArchive(videoId, videoInfoArchive);

        // 移动封面和缩略图
        moveImage(MinioKey.PENDING_PREFIX, MinioKey.PUBLIC_PREFIX, videoInfo.getVideoCover());

        // 移动视频文件
        List <VideoInfoFile> videoInfoFiles = videoInfoFileMapper.selectByVideoId(videoId);
        List <String> videoBaseKeyList = videoInfoFiles.stream().map(VideoInfoFile::getFilePath).toList();
        moveVideoFiles(MinioKey.PENDING_PREFIX, MinioKey.PUBLIC_PREFIX, videoBaseKeyList);


        // 通知用户视频被恢复
        CompletableFuture <Void> completableFuture = userMessageService.sendVideoRelatedMessage(videoInfo.getUserId(),
                                                                                                videoId,
                                                                                                "您的视频：[" + videoInfo.getVideoName() + "]已被管理员恢复");
        CompletableFuture.allOf(completableFuture).exceptionally(e ->
                                                                 {
                                                                     log.warn("发送恢复视频消息失败，异常信息：{}", e.toString());
                                                                     return null;
                                                                 });
    }

    /**
     * 切换视频推荐状态
     *
     * @param videoId 视频ID
     */
    @Transactional
    public void toggleVideoRecommend(long videoId)
    {
        VideoInfo videoInfo = videoInfoMapper.selectByVideoId(videoId);

        if (videoInfo == null)
        {
            throw new BusinessException("没有该视频");
        }

        // 提前构建好查询推荐视频数量的条件
        VideoInfoQuery query = new VideoInfoQuery();
        query.setRecommendType(1);

        // 如果是推荐操作，需要查询有没有达到推荐上限
        if (Objects.equals(videoInfo.getRecommendType(), (short) RecommendType.UNRECOMMENDED.getType()))
        {
            // 查询当前推荐视频数量有没有到最大值

            Integer firstCount = videoInfoMapper.selectCount(query);
            if (firstCount >= adminConfig.getMaxRecommendVideoNumber())
            {
                throw new BusinessException("超过最大推荐视频数量：" + adminConfig.getMaxRecommendVideoNumber());
            }
        }

        // 修改
        videoInfoMapper.toggleRecommendType(videoId);

        // 再查询
        Integer secondCount = videoInfoMapper.selectCount(query);
        if (secondCount > adminConfig.getMaxRecommendVideoNumber())
        {
            throw new BusinessException("超过最大推荐视频数量：" + adminConfig.getMaxRecommendVideoNumber());
        }
    }

    /**
     * 删除用户视频
     *
     * @param userId  发布者ID
     * @param videoId 视频ID
     * @param detail  删除详情
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteVideo(long userId, long videoId, String detail)
    {
        VideoInfo videoInfo = videoInfoMapper.selectByVideoId(videoId);
        // 放到这里只是为了让两个分支都有同样的通知代码逻辑
        String videoName;

        // 如果正式表没有数据，就说明视频还没发布，直接删除上传表中的数据，不可恢复
        if (videoInfo == null)
        {
            VideoInfoUpload videoInfoUpload = videoInfoUploadMapper.selectByVideoId(videoId);
            if (videoInfoUpload == null)
            {
                return;
            }
            // 如果不是该用户的，返回用户信息错误
            if (!Objects.equals(videoInfoUpload.getUserId(), userId))
            {
                throw new BusinessException("不是该用户的视频");
            }

            videoName = videoInfoUpload.getVideoName();

            // 获取删除路径列表
            List <VideoInfoFileUpload> uploadFileList = videoInfoFileUploadMapper.selectByVideoId(videoId);
            List <String> deletePathList;
            if (uploadFileList == null || uploadFileList.isEmpty())
            {
                deletePathList = List.of();
            }
            else
            {
                // 这个时候所有视频文件都在PENDING状态
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

            // 删除文件从属表
            mediaOwnershipMapper.deleteBatchByObjectKey(deleteKeys);

            // --- 在事务提交后把删除路径交给MQ ---

            // 删除视频文件
            // 额外校验下是否存在，有的时候可能会出现没有文件的情况
            if (!deletePathList.isEmpty())
            {
                addKeysToVideoDeleteQueueAfterCommit(deletePathList);
            }

            // 删除封面文件
            if (coverKey != null && !coverKey.isBlank())
            {
                addKeysToImageDeleteQueueAfterCommit(List.of(coverKey, FileUtil.constructThumbnailName(coverKey)));
            }
        }
        // 否则，这是个已发布的视频，我们将其归档到存档表中，文件移动到PENDING，这样别人看不到
        else
        {
            // 如果 视频不属于该用户
            if (!Objects.equals(videoInfo.getUserId(), userId))
            {
                throw new BusinessException("不是该用户的视频");
            }

            videoName = videoInfo.getVideoName();

            // 给用户扣除发布视频时获得的硬币
            userInfoMapper.decreaseCoinForVideoDelete(userId,
                                                      systemConfigRedisRepository.getSystemConfig().getRewardsPreUpload());

            // 数据迁移前先查出数据
            List <VideoInfoFile> videoInfoFiles = videoInfoFileMapper.selectByVideoId(videoId);

            // 数据迁移和删除
            archiveVideo(userId, videoId, detail, videoInfo);

            // 移动封面和缩略图
            moveImage(MinioKey.PUBLIC_PREFIX, MinioKey.PENDING_PREFIX, videoInfo.getVideoCover());

            // 移动视频文件
            List <String> videoBaseKeyList = videoInfoFiles.stream().map(VideoInfoFile::getFilePath).toList();
            moveVideoFiles(MinioKey.PUBLIC_PREFIX, MinioKey.PENDING_PREFIX, videoBaseKeyList);
        }

        // 通知用户视频被删除
        CompletableFuture <Void> completableFuture = userMessageService.sendVideoRelatedMessage(userId,
                                                                                                videoId,
                                                                                                "您的视频：[" + videoName + "]已被管理员删除，原因：\n" + detail);
        CompletableFuture.allOf(completableFuture).exceptionally(e ->
                                                                 {
                                                                     log.warn("发送删除视频消息失败，异常信息：{}", e.toString());
                                                                     return null;
                                                                 });
    }

    /**
     * 在事务提交后将视频 key 放入删除队列
     */
    private void addKeysToVideoDeleteQueueAfterCommit(List <String> keys)
    {
        if (keys == null || keys.isEmpty())
        {
            return;
        }

        if (!TransactionSynchronizationManager.isSynchronizationActive())
        {
            mqRepository.addKeysToVideoDeleteQueue(keys);
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization()
        {
            @Override
            public void afterCommit()
            {
                mqRepository.addKeysToVideoDeleteQueue(keys);
            }
        });
    }

    /**
     * 在事务提交后将图片 key 放入删除队列
     */
    private void addKeysToImageDeleteQueueAfterCommit(List <String> keys)
    {
        if (keys == null || keys.isEmpty())
        {
            return;
        }

        if (!TransactionSynchronizationManager.isSynchronizationActive())
        {
            mqRepository.addKeysToImageDeleteQueue(keys);
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization()
        {
            @Override
            public void afterCommit()
            {
                mqRepository.addKeysToImageDeleteQueue(keys);
            }
        });
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
        ResponseVO <Void> imageResult = imageFeignClient.batchMove(imageKeyMap);
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
        ResponseVO <Void> videoResult = videoFileFeignClient.batchMoveDirectory(videoDirectoryMap);
        if (!ResponseCode.SUCCESS.getCode().equals(videoResult.getCode()))
        {
            throw new RuntimeException("视频文件移动失败");
        }
    }

    /**
     * 视频归档
     *
     * @param userId    用户ID
     * @param videoId   视频ID
     * @param detail    详细信息
     * @param videoInfo 视频信息
     */
    private void archiveVideo(long userId, long videoId, String detail, VideoInfo videoInfo)
    {
        // --- 将所有数据迁移到 archive 表 ---

        // video_info
        // 我们认为 video_info 代表了删除的操作，因此其他表如果存在原来的记录，将不会认为是重复删除，并会将archive中对应的记录清除并重新录入
        VideoInfoArchive videoInfoArchive = videoInfoArchiveMapper.selectByVideoId(videoId);
        if (videoInfoArchive != null)
        {
            throw new BusinessException("请勿重复删除");
        }
        videoInfoArchive = new VideoInfoArchive();
        videoInfoArchive.setDeleteTime(LocalDateTime.now());
        videoInfoArchive.setDeleterType(DeleterType.ADMIN.getValue());
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

        // video_comment
        VideoCommentQuery videoCommentQuery = new VideoCommentQuery();
        videoCommentQuery.setVideoId(videoId);
        List <VideoComment> videoCommentList = videoCommentMapper.selectList(videoCommentQuery);
        VideoCommentArchiveQuery videoCommentArchiveQuery = new VideoCommentArchiveQuery();
        videoCommentArchiveQuery.setVideoId(videoId);
        videoCommentArchiveMapper.deleteByParam(videoCommentArchiveQuery);
        archiveBatch(videoCommentList, VideoCommentArchive::new, videoCommentArchiveMapper);

        // video_danmaku
        VideoDanmakuQuery videoDanmakuQuery = new VideoDanmakuQuery();
        videoDanmakuQuery.setVideoId(videoId);
        List <VideoDanmaku> videoDanmakuList = videoDanmakuMapper.selectList(videoDanmakuQuery);
        VideoDanmakuArchiveQuery videoDanmakuArchiveQuery = new VideoDanmakuArchiveQuery();
        videoDanmakuArchiveQuery.setVideoId(videoId);
        videoDanmakuArchiveMapper.deleteByParam(videoDanmakuArchiveQuery);
        archiveBatch(videoDanmakuList, VideoDanmakuArchive::new, videoDanmakuArchiveMapper);

        // user_comment_action
        UserCommentActionQuery userCommentActionQuery = new UserCommentActionQuery();
        userCommentActionQuery.setVideoId(videoId);
        List <UserCommentAction> userCommentActionList = userCommentActionMapper.selectList(userCommentActionQuery);
        UserCommentActionArchiveQuery userCommentActionArchiveQuery = new UserCommentActionArchiveQuery();
        userCommentActionArchiveQuery.setVideoId(videoId);
        userCommentActionArchiveMapper.deleteByParam(userCommentActionArchiveQuery);
        archiveBatch(userCommentActionList, UserCommentActionArchive::new, userCommentActionArchiveMapper);

        // user_video_action
        UserVideoActionQuery userVideoActionQuery = new UserVideoActionQuery();
        userVideoActionQuery.setVideoId(videoId);
        List <UserVideoAction> userVideoActionList = userVideoActionMapper.selectList(userVideoActionQuery);
        UserVideoActionArchiveQuery userVideoActionArchiveQuery = new UserVideoActionArchiveQuery();
        userVideoActionArchiveQuery.setVideoId(videoId);
        userVideoActionArchiveMapper.deleteByParam(userVideoActionArchiveQuery);
        archiveBatch(userVideoActionList, UserVideoActionArchive::new, userVideoActionArchiveMapper);

        // --- 删除原业务表数据 ---

        userCommentActionMapper.deleteByParam(userCommentActionQuery);
        userVideoActionMapper.deleteByParam(userVideoActionQuery);
        videoDanmakuMapper.deleteByParam(videoDanmakuQuery);
        videoCommentMapper.deleteByParam(videoCommentQuery);
        videoInfoFileMapper.deleteByParam(videoInfoFileQuery);
        videoInfoMapper.deleteByVideoId(videoId);

        // --- 清理 upload 表 ---

        VideoInfoFileUploadQuery videoInfoFileUploadQuery = new VideoInfoFileUploadQuery();
        videoInfoFileUploadQuery.setVideoId(videoId);
        videoInfoFileUploadMapper.deleteByParam(videoInfoFileUploadQuery);

        VideoInfoUploadQuery videoInfoUploadQuery = new VideoInfoUploadQuery();
        videoInfoUploadQuery.setVideoId(videoId);
        videoInfoUploadMapper.deleteByParam(videoInfoUploadQuery);

        // 更新 UserState
        accountRedisRepository.deleteUserState(userId);
    }

    /**
     * 检查是否会从表中查询到空数据
     *
     * @param query        查询条件
     * @param targetMapper 表对应mapper
     * @param <P>          查询条件类型
     */
    private <P> void assertRecoverTargetEmpty(P query, BaseMapper <?, P> targetMapper)
    {
        Integer count = targetMapper.selectCount(query);
        if (count != null && count > 0)
        {
            throw new BusinessException("视频无法恢复，因为目标表数据存在冲突，请联系管理员");
        }
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

    private VideoInfo loadVideoArchive(long videoId, VideoInfoArchive videoInfoArchive)
    {
        // --- 将所有数据迁移到主表 ---

        // video_info
        VideoInfo videoInfo = videoInfoMapper.selectByVideoId(videoId);
        // 对应删除操作，我们认为 video_info 的存在代表整个数据得到了恢复
        // 虽然主表数据和archive数据同时存在的情况在我们的业务设计中几乎不可能，但一旦出现我们就需要主动介入解决
        if (videoInfo != null)
        {
            throw new BusinessException("视频无法回复，因为数据存在冲突，请联系管理员");
        }
        videoInfo = new VideoInfo();
        BeanUtils.copyProperties(videoInfoArchive, videoInfo);
        videoInfoMapper.insert(videoInfo);

        // video_info_upload
        if (videoInfoUploadMapper.selectByVideoId(videoId) != null)
        {
            throw new BusinessException("视频无法恢复，因为上传表数据存在冲突，请联系管理员");
        }
        VideoInfoUpload videoInfoUpload = new VideoInfoUpload();
        BeanUtils.copyProperties(videoInfoArchive, videoInfoUpload);
        videoInfoUpload.setStatus(VideoStatus.REVIEW_SUCCESS.getStatus());
        videoInfoUploadMapper.insert(videoInfoUpload);

        // video_info_file
        VideoInfoFileArchiveQuery videoInfoFileArchiveQuery = new VideoInfoFileArchiveQuery();
        videoInfoFileArchiveQuery.setVideoId(videoId);
        List <VideoInfoFileArchive> videoInfoFileArchiveList = videoInfoFileArchiveMapper.selectList(videoInfoFileArchiveQuery);

        VideoInfoFileQuery videoInfoFileQuery = new VideoInfoFileQuery();
        videoInfoFileQuery.setVideoId(videoId);
        assertRecoverTargetEmpty(videoInfoFileQuery, videoInfoFileMapper);
        archiveBatch(videoInfoFileArchiveList, VideoInfoFile::new, videoInfoFileMapper);

        // video_info_file_upload
        VideoInfoFileUploadQuery videoInfoFileUploadQuery = new VideoInfoFileUploadQuery();
        videoInfoFileUploadQuery.setVideoId(videoId);
        assertRecoverTargetEmpty(videoInfoFileUploadQuery, videoInfoFileUploadMapper);
        List <VideoInfoFileUpload> videoInfoFileUploadList = videoInfoFileArchiveList.stream().map(archive ->
                                                                                                   {
                                                                                                       VideoInfoFileUpload upload = new VideoInfoFileUpload();
                                                                                                       BeanUtils.copyProperties(
                                                                                                               archive,
                                                                                                               upload);
                                                                                                       upload.setUpdateType(
                                                                                                               UpdateType.NO_UPDATE.getUpdateType());
                                                                                                       upload.setTransferResult(
                                                                                                               VideoFileStatus.TRANSCODING_SUCCESS.getStatus());
                                                                                                       return upload;
                                                                                                   }).toList();
        if (!videoInfoFileUploadList.isEmpty())
        {
            videoInfoFileUploadMapper.insertBatch(videoInfoFileUploadList);
        }

        // video_comment
        VideoCommentArchiveQuery videoCommentArchiveQuery = new VideoCommentArchiveQuery();
        videoCommentArchiveQuery.setVideoId(videoId);
        List <VideoCommentArchive> videoCommentArchiveList = videoCommentArchiveMapper.selectList(videoCommentArchiveQuery);

        VideoCommentQuery videoCommentQuery = new VideoCommentQuery();
        videoCommentQuery.setVideoId(videoId);
        assertRecoverTargetEmpty(videoCommentQuery, videoCommentMapper);
        archiveBatch(videoCommentArchiveList, VideoComment::new, videoCommentMapper);

        // video_danmaku
        VideoDanmakuArchiveQuery videoDanmakuArchiveQuery = new VideoDanmakuArchiveQuery();
        videoDanmakuArchiveQuery.setVideoId(videoId);
        List <VideoDanmakuArchive> videoDanmakuArchiveList = videoDanmakuArchiveMapper.selectList(videoDanmakuArchiveQuery);

        VideoDanmakuQuery videoDanmakuQuery = new VideoDanmakuQuery();
        videoDanmakuQuery.setVideoId(videoId);
        assertRecoverTargetEmpty(videoDanmakuQuery, videoDanmakuMapper);
        archiveBatch(videoDanmakuArchiveList, VideoDanmaku::new, videoDanmakuMapper);

        // user_comment_action
        UserCommentActionArchiveQuery userCommentActionArchiveQuery = new UserCommentActionArchiveQuery();
        userCommentActionArchiveQuery.setVideoId(videoId);
        List <UserCommentActionArchive> userCommentActionArchiveList = userCommentActionArchiveMapper.selectList(
                userCommentActionArchiveQuery);

        UserCommentActionQuery userCommentActionQuery = new UserCommentActionQuery();
        userCommentActionQuery.setVideoId(videoId);
        assertRecoverTargetEmpty(userCommentActionQuery, userCommentActionMapper);
        archiveBatch(userCommentActionArchiveList, UserCommentAction::new, userCommentActionMapper);

        // user_video_action
        UserVideoActionArchiveQuery userVideoActionArchiveQuery = new UserVideoActionArchiveQuery();
        userVideoActionArchiveQuery.setVideoId(videoId);
        List <UserVideoActionArchive> userVideoActionArchiveList = userVideoActionArchiveMapper.selectList(
                userVideoActionArchiveQuery);

        UserVideoActionQuery userVideoActionQuery = new UserVideoActionQuery();
        userVideoActionQuery.setVideoId(videoId);
        assertRecoverTargetEmpty(userVideoActionQuery, userVideoActionMapper);
        archiveBatch(userVideoActionArchiveList, UserVideoAction::new, userVideoActionMapper);

        // --- 清理 archive 表数据 ---

        userCommentActionArchiveMapper.deleteByParam(userCommentActionArchiveQuery);
        userVideoActionArchiveMapper.deleteByParam(userVideoActionArchiveQuery);
        videoDanmakuArchiveMapper.deleteByParam(videoDanmakuArchiveQuery);
        videoCommentArchiveMapper.deleteByParam(videoCommentArchiveQuery);
        videoInfoFileArchiveMapper.deleteByParam(videoInfoFileArchiveQuery);
        videoInfoArchiveMapper.deleteByVideoId(videoId);

        // 更新 UserState
        accountRedisRepository.deleteUserState(videoInfo.getUserId());

        return videoInfo;
    }
}
