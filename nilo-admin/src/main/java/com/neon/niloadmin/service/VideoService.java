package com.neon.niloadmin.service;

import cn.hutool.core.lang.Snowflake;
import com.neon.niloadmin.config.AdminConfig;
import com.neon.niloadmin.mapper.*;
import com.neon.niloadmin.repository.rabbitmq.MqRepository;
import com.neon.nilocommon.entity.constants.Constants;
import com.neon.nilocommon.entity.dto.VideoInfoUploadAdminJoinDTO;
import com.neon.nilocommon.entity.enums.videoInfoFileUpload.UpdateType;
import com.neon.nilocommon.entity.enums.videoInfoFileUpload.VideoFileStatus;
import com.neon.nilocommon.entity.enums.videoInfoUpload.VideoStatus;
import com.neon.nilocommon.entity.po.*;
import com.neon.nilocommon.entity.query.*;
import com.neon.nilocommon.exception.BusinessException;
import com.neon.nilocommon.util.FileUtil;
import com.neon.nilocommon.util.StringUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.util.List;
import java.util.function.Supplier;

@Slf4j
@RequiredArgsConstructor
@Service
public class VideoService
{

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

    // --- other ---

    private final VideoInfoDocService videoInfoDocService;

    private final MqRepository mqRepository;

    private final AdminConfig adminConfig;

    private final Snowflake snowflake;

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

        // 将获取的记录转化之后插入到video_info_file表
        List <VideoInfoFile> infoFileList = infoFileUploadList.stream().map(infoFileUpload ->
                                                                            {
                                                                                VideoInfoFile infoFile = new VideoInfoFile();
                                                                                BeanUtils.copyProperties(infoFileUpload,
                                                                                                         infoFile);
                                                                                return infoFile;
                                                                            }).toList();
        videoInfoFileMapper.insertBatch(infoFileList);

        // 删除不需要的视频（所有fileId不存在于新出现的文件中的视频文件）
        List <Long> remainFileIdList = infoFileList.stream().map(VideoInfoFile::getFileId).toList();
        String rootFilePath = Path.of(adminConfig.getRootFilePath(), Constants.FILE_FOLDER_NAME).normalize().toString();
        List <VideoInfoFile> deleteFileList = oldFileList.stream()
                                                         .filter(oldFile -> !remainFileIdList.contains(oldFile.getFileId())) // 不在新文件列表中的文件
                                                         .toList();
        List <String> deletePathList = deleteFileList.stream()
                                                     .map(VideoInfoFile::getFilePath)
                                                     .filter(filePath -> filePath != null && !filePath.isBlank())
                                                     .map(filePath -> Path.of(rootFilePath, filePath).normalize().toString())
                                                     .filter(absPath -> StringUtil.isValidPath(absPath, rootFilePath))
                                                     .filter(FileUtil::fileExists)
                                                     .distinct()
                                                     .toList();

        // 删除旧的视频文件的弹幕
        Integer deletedDanmakuCount = videoDanmakuMapper.deleteByFileIdBatch(deleteFileList.stream()
                                                                                           .map(VideoInfoFile::getFileId)
                                                                                           .toList());
        // 更新videoInfo信息，因为我们删除了旧视频文件的弹幕
        videoInfoMapper.decreaseByField(videoId, "danmaku_count", deletedDanmakuCount);

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
                videoInfoDocService.deleteVideoInfoDoc(videoId);
                throw e;
            }
            // 删除失败逻辑
            catch (Exception exceptionCausedByDeleteFailed)
            {
                log.error("失效的ES记录，ID={}，videoInfo={}", videoId, videoInfo);
                throw exceptionCausedByDeleteFailed;
            }
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

}
