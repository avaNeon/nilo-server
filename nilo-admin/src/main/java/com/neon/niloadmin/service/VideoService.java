package com.neon.niloadmin.service;

import cn.hutool.core.lang.Snowflake;
import com.neon.niloadmin.config.AdminConfig;
import com.neon.niloadmin.mapper.*;
import com.neon.niloadmin.repository.elasticsearch.VideoInfoDocRepository;
import com.neon.niloadmin.repository.rabbitmq.MqRepository;
import com.neon.niloadmin.repository.redis.AccountRedisRepository;
import com.neon.nilocommon.entity.constants.Constants;
import com.neon.nilocommon.entity.dto.VideoInfoUploadAdminJoinDTO;
import com.neon.nilocommon.entity.enums.ResponseCode;
import com.neon.nilocommon.entity.enums.videoInfo.RecommendType;
import com.neon.nilocommon.entity.enums.videoInfoArchive.DeleterType;
import com.neon.nilocommon.entity.enums.videoInfoFileUpload.UpdateType;
import com.neon.nilocommon.entity.enums.videoInfoFileUpload.VideoFileStatus;
import com.neon.nilocommon.entity.enums.videoInfoUpload.VideoStatus;
import com.neon.nilocommon.entity.po.*;
import com.neon.nilocommon.entity.po.document.VideoInfoDoc;
import com.neon.nilocommon.entity.query.*;
import com.neon.nilocommon.exception.BusinessException;
import com.neon.nilocommon.util.FileUtil;
import com.neon.nilocommon.util.PageCalculator;
import com.neon.nilocommon.util.StringUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

@Slf4j
@RequiredArgsConstructor
@Service
public class VideoService
{
    /* Service */

    private final UserMessageService userMessageService;

    private final VideoInfoDocService videoInfoDocService;

    /* Repository */

    private final UserInfoMapper <UserInfo, UserInfoQuery> userInfoMapper;

    // --- upload ---

    private final VideoInfoUploadMapper <VideoInfoUpload, VideoInfoUploadQuery> videoInfoUploadMapper;

    private final VideoInfoFileUploadMapper <VideoInfoFileUpload, VideoInfoFileUploadQuery> videoInfoFileUploadMapper;

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

    private final VideoInfoDocRepository videoInfoDocRepository;

    private final AccountRedisRepository accountRedisRepository;

    private final MqRepository mqRepository;

    /* Other */

    private final AdminConfig adminConfig;

    private final Snowflake snowflake;

    /* 常量 */

    private static final String reviewSuccessMessage = "您的视频已经通过审核";

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
        if (!reviewResult)
        {
            // 查询正常表中已存在的视频文件，用于状态隔离——不能删除正常表还在引用的文件
            VideoInfoFileQuery infoFileQuery = new VideoInfoFileQuery();
            infoFileQuery.setVideoId(videoId);

            List <VideoInfoFile> activeFileList = videoInfoFileMapper.selectList(infoFileQuery);

            // 未被修改的视频文件ID列表
            List <Long> activeFileIdList = activeFileList.stream()
                                                         .map(VideoInfoFile::getFileId)
                                                         .filter(Objects::nonNull)
                                                         .toList();

            List <VideoInfoFileUpload> infoFileUploadList = videoInfoFileUploadMapper.selectByVideoId(videoId);

            // 过滤出不在正常表中的上传文件记录（正常表已引用的文件不能删除）
            List <VideoInfoFileUpload> orphanUploadList = infoFileUploadList.stream()
                                                                            .filter(file -> !activeFileIdList.contains(file.getFileId()))
                                                                            .toList();

            List <String> deletePathList = buildDeletePathList(orphanUploadList);

            // 只清空不在正常表中的上传文件记录，保证状态隔离
            if (!orphanUploadList.isEmpty())
            {
                List <Long> deletedUploadFileIdList = orphanUploadList.stream()
                                                                      .map(VideoInfoFileUpload::getFileId)
                                                                      .filter(Objects::nonNull)
                                                                      .toList();
                if (!deletedUploadFileIdList.isEmpty())
                {
                    videoInfoFileUploadMapper.deleteBatchByFileId(deletedUploadFileIdList);
                }
            }

            // 将视频文件交给负责删除的MQ
            if (!deletePathList.isEmpty())
            {
                mqRepository.addPathList2DeleteQueue(deletePathList);
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
            return;
        }

        VideoInfoUpload infoUpload = videoInfoUploadMapper.selectByVideoId(videoId);

        VideoInfo videoInfo = videoInfoMapper.selectByVideoId(videoId);

        // 提前拿出旧封面看看
        String oldCover = videoInfo == null ? null : videoInfo.getVideoCover();

        if (videoInfo == null)
        {
            videoInfo = new VideoInfo();

            Long userId = infoUpload.getUserId();
            if (userId == null)
            {
                throw new BusinessException("数据库记录错误，没有用户ID！");
            }
            userInfoMapper.increaseCoin(userId, adminConfig.getCoinBonusPerVideo());
        }

        // 更新/填入videoInfo信息
        BeanUtils.copyProperties(infoUpload, videoInfo);
        videoInfoMapper.insertOrUpdate(videoInfo);

        // 删除旧的video_info_file记录（DB层面）
        VideoInfoFileQuery infoFileQuery = new VideoInfoFileQuery();
        infoFileQuery.setVideoId(videoId);
        List <VideoInfoFile> oldFileList = videoInfoFileMapper.selectList(infoFileQuery);
        videoInfoFileMapper.deleteByParam(infoFileQuery);

        // 获取videoInfoFileUpload记录
        VideoInfoFileUploadQuery videoInfoFileUploadQuery = new VideoInfoFileUploadQuery();
        videoInfoFileUploadQuery.setVideoId(videoId);
        List <VideoInfoFileUpload> infoFileUploadList = videoInfoFileUploadMapper.selectList(videoInfoFileUploadQuery);

        // 检查空路径文件记录
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
                                                                                                                 .isBlank())
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
        String rootFilePath = Path.of(adminConfig.getRootFilePath(), Constants.FILE_FOLDER_NAME).normalize().toString();
        List <VideoInfoFile> deleteFileList = oldFileList.stream()
                                                         .filter(oldFile -> !remainFileIdList.contains(oldFile.getFileId())) // 不在新文件列表中的文件
                                                         .toList();

        // toList() 返回不可变 List 类型，所以这里给它构造一下
        List <String> deletePathList = new ArrayList <>(deleteFileList.stream()
                                                                      .map(VideoInfoFile::getFilePath)
                                                                      .filter(filePath -> filePath != null && !filePath.isBlank())
                                                                      .map(filePath -> Path.of(rootFilePath, filePath)
                                                                                           .normalize()
                                                                                           .toString())
                                                                      .filter(absPath -> StringUtil.isValidPath(rootFilePath,
                                                                                                                absPath))
                                                                      .filter(FileUtil::fileExists)
                                                                      .distinct()
                                                                      .toList());

        // 如果封面有变动
        if (oldCover != null && !oldCover.isBlank() && !Objects.equals(oldCover, infoUpload.getVideoCover()))
        {
            String oldCoverPath = Path.of(rootFilePath, Constants.COVER_FOLDER_NAME, oldCover).normalize().toString();
            if (StringUtil.isValidPath(rootFilePath, oldCoverPath) && FileUtil.fileExists(oldCoverPath))
            {
                // 那么就删除旧封面
                deletePathList.add(oldCoverPath);
            }
        }

        // 删除旧的视频文件的弹幕
        if (!deleteFileList.isEmpty())
        {
            Integer deletedDanmakuCount = videoDanmakuMapper.deleteByFileIdBatch(deleteFileList.stream()
                                                                                               .map(VideoInfoFile::getFileId)
                                                                                               .toList());
            // 更新videoInfo信息，因为我们删除了旧视频文件的弹幕
            if (deletedDanmakuCount != null && deletedDanmakuCount > 0)
            {
                videoInfoMapper.decreaseByField(videoId, "danmaku_count", deletedDanmakuCount);
                videoInfo.setDanmakuCount(Math.max((videoInfo.getDanmakuCount() == null ? 0 : videoInfo.getDanmakuCount()) - deletedDanmakuCount,
                                                   0));
            }
        }

        // 更新 UserState
        accountRedisRepository.deleteUserState(videoInfo.getUserId());

        // 将记录保存到ES中
        videoInfoDocService.saveVideoInfoDoc(videoInfo);

        // 将需要删除的文件放在MQ队列中
        try
        {
            if (!deletePathList.isEmpty())
            {
                mqRepository.addPathList2DeleteQueue(deletePathList);
            }
        }
        // 放入MQ失败，回滚ES记录
        catch (Exception e)
        {
            try
            {
                videoInfoDocRepository.deleteById(videoId);
                throw e;
            }
            // 删除失败逻辑
            catch (Exception exceptionCausedByDeleteFailed)
            {
                log.error("失效的ES记录，ID={}，videoInfo={}", videoId, videoInfo);
                throw exceptionCausedByDeleteFailed;
            }
        }

        // 异步向用户发送系统消息，通知用户视频审核状态
        CompletableFuture <Void> completableFuture = userMessageService.sendVideoReviewMessage(videoId, reviewSuccessMessage);

        CompletableFuture.allOf(completableFuture).exceptionally(e ->
                                                                 {
                                                                     log.warn("发送审核通过消息失败，异常信息：{}", e.toString());
                                                                     return null;
                                                                 });
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
        userInfoMapper.increaseCoin(videoInfoArchive.getUserId(), adminConfig.getCoinBonusPerVideo());


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
                                                                                                       upload.setUploadId(
                                                                                                               snowflake.nextId());
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

        // 将恢复的视频数据插入ES中
        VideoInfoDoc videoInfoDoc = new VideoInfoDoc();
        BeanUtils.copyProperties(videoInfo, videoInfoDoc);
        videoInfoDocRepository.save(videoInfoDoc);

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
        // 如果正式表没有数据，就删除上传表中的数据
        if (videoInfo == null)
        {
            deleteUnpublishedVideo(videoId, detail);
            return;
        }

        // 如果 视频不属于该用户
        if (!Objects.equals(videoInfo.getUserId(), userId))
        {
            throw new BusinessException(ResponseCode.NOT_FOUND);
        }

        // 给用户扣除发布视频时获得的硬币
        userInfoMapper.decreaseCoinForVideoDelete(userId, adminConfig.getCoinBonusPerVideo());

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

        // TODO 如果以后要把记录存入redis中，那么这里也要删除redis中留存的记录

        // 删除ES记录
        videoInfoDocRepository.deleteById(videoId);

        // 通知用户视频被删除
        CompletableFuture <Void> completableFuture = userMessageService.sendVideoRelatedMessage(userId,
                                                                                                videoId,
                                                                                                "您的视频：[" + videoInfo.getVideoName() + "]已被管理员删除，原因：\n" + detail);
        CompletableFuture.allOf(completableFuture).exceptionally(e ->
                                                                 {
                                                                     log.warn("发送删除视频消息失败，异常信息：{}", e.toString());
                                                                     return null;
                                                                 });
    }

    /**
     * 删除未发布视频（正式表无数据时，清理上传表数据）
     *
     * @param videoId 视频ID
     * @param detail  删除原因
     */
    private void deleteUnpublishedVideo(long videoId, String detail)
    {
        VideoInfoUpload videoInfoUpload = videoInfoUploadMapper.selectByVideoId(videoId);
        if (videoInfoUpload == null)
        {
            return;
        }

        List <VideoInfoFileUpload> uploadFileList = videoInfoFileUploadMapper.selectByVideoId(videoId);
        List <String> deletePathList = buildDeletePathList(uploadFileList);

        VideoInfoFileUploadQuery videoInfoFileUploadQuery = new VideoInfoFileUploadQuery();
        videoInfoFileUploadQuery.setVideoId(videoId);
        videoInfoFileUploadMapper.deleteByParam(videoInfoFileUploadQuery);

        VideoInfoUploadQuery videoInfoUploadQuery = new VideoInfoUploadQuery();
        videoInfoUploadQuery.setVideoId(videoId);
        videoInfoUploadMapper.deleteByParam(videoInfoUploadQuery);

        if (!deletePathList.isEmpty())
        {
            mqRepository.addPathList2DeleteQueue(deletePathList);
        }

        // 通知用户视频被删除
        CompletableFuture <Void> completableFuture = userMessageService.sendVideoRelatedMessage(videoInfoUpload.getUserId(),
                                                                                                videoId,
                                                                                                "您的视频：[" + videoInfoUpload.getVideoName() + "]已被管理员删除，原因：\n" + detail);
        CompletableFuture.allOf(completableFuture).exceptionally(e ->
                                                                 {
                                                                     log.warn("发送删除视频消息失败，异常信息：{}", e.toString());
                                                                     return null;
                                                                 });
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

    /**
     * 抽取文件路径，转换为绝对路径列表
     *
     * @param infoFileUploadList 上传文件列表
     * @return 绝对路径列表
     */
    private List <String> buildDeletePathList(List <VideoInfoFileUpload> infoFileUploadList)
    {
        if (infoFileUploadList == null || infoFileUploadList.isEmpty())
        {
            return List.of();
        }

        String rootFilePath = Path.of(adminConfig.getRootFilePath(), Constants.FILE_FOLDER_NAME).normalize().toString();

        return infoFileUploadList.stream()
                                 .map(VideoInfoFileUpload::getFilePath)
                                 .filter(filePath -> filePath != null && !filePath.isBlank())
                                 .map(filePath -> Path.of(rootFilePath, filePath).normalize().toString())
                                 .filter(absPath -> StringUtil.isValidPath(rootFilePath, absPath))
                                 .filter(FileUtil::fileExists)
                                 .distinct()
                                 .toList();
    }

}
