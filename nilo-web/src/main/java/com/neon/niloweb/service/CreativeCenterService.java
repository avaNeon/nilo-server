package com.neon.niloweb.service;

import cn.hutool.core.lang.Snowflake;
import com.neon.nilocommon.entity.constants.Constants;
import com.neon.nilocommon.entity.dto.VideoInfoUploadJoinDTO;
import com.neon.nilocommon.entity.enums.ResponseCode;
import com.neon.nilocommon.entity.enums.videoInfoArchive.DeleterType;
import com.neon.nilocommon.entity.enums.videoInfoFileUpload.VideoFileStatus;
import com.neon.nilocommon.entity.enums.videoInfoUpload.VideoStatus;
import com.neon.nilocommon.entity.po.*;
import com.neon.nilocommon.entity.po.redis.TokenUserInfo;
import com.neon.nilocommon.entity.query.*;
import com.neon.nilocommon.entity.vo.VideoInfoFileUploadVO;
import com.neon.nilocommon.entity.vo.VideoStatusCountVO;
import com.neon.nilocommon.entity.vo.comment.CommentManagementVO;
import com.neon.nilocommon.entity.vo.danmaku.DanmakuManagementVO;
import com.neon.nilocommon.exception.BusinessException;
import com.neon.nilocommon.util.FileUtil;
import com.neon.nilocommon.util.PageCalculator;
import com.neon.niloweb.config.SystemConfig;
import com.neon.niloweb.config.WebConfig;
import com.neon.niloweb.mapper.*;
import com.neon.niloweb.repository.elasticsearch.VideoInfoDocRepository;
import com.neon.niloweb.repository.rabbitmq.VideoMqRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Service
public class CreativeCenterService
{
    private final SystemConfig systemConfig;

    private final UserInfoMapper <UserInfo, UserInfoQuery> userInfoMapper;

    // ----- upload -----

    private final VideoInfoUploadMapper <VideoInfoUpload, VideoInfoUploadQuery> videoInfoUploadMapper;

    private final VideoInfoFileUploadMapper <VideoInfoFileUpload, VideoInfoFileUploadQuery> videoInfoFileUploadMapper;

    // ----- info -----

    private final VideoInfoMapper <VideoInfo, VideoInfoQuery> videoInfoMapper;

    private final VideoInfoFileMapper <VideoInfoFile, VideoInfoFileQuery> videoInfoFileMapper;

    private final VideoCommentMapper <VideoComment, VideoCommentQuery> videoCommentMapper;

    private final VideoDanmakuMapper <VideoDanmaku, VideoDanmakuQuery> videoDanmakuMapper;

    private final UserCommentActionMapper <UserCommentAction, UserCommentActionQuery> userCommentActionMapper;

    private final UserVideoActionMapper <UserVideoAction, UserVideoActionQuery> userVideoActionMapper;

    // ----- archive -----

    private final VideoInfoArchiveMapper <VideoInfoArchive, VideoInfoArchiveQuery> videoInfoArchiveMapper;

    private final VideoInfoFileArchiveMapper <VideoInfoFileArchive, VideoInfoFileArchiveQuery> videoInfoFileArchiveMapper;

    private final VideoCommentArchiveMapper <VideoCommentArchive, VideoCommentArchiveQuery> videoCommentArchiveMapper;

    private final VideoDanmakuArchiveMapper <VideoDanmakuArchive, VideoDanmakuArchiveQuery> videoDanmakuArchiveMapper;

    private final UserCommentActionArchiveMapper <UserCommentActionArchive, UserCommentActionArchiveQuery> userCommentActionArchiveMapper;

    private final UserVideoActionArchiveMapper <UserVideoActionArchive, UserVideoActionArchiveQuery> userVideoActionArchiveMapper;

    // ----- ElasticSearch repository -----
    private final VideoInfoDocRepository videoInfoDocRepository;

    // ----- other -----

    private final Snowflake snowflake;

    private final VideoMqRepository videoMqRepository;

    private final WebConfig webConfig;

    /**
     * 视频上传<hr/>
     * 视频上传流程：<br/>
     * <li> 1. 先将字段整合成videoInfoUpload类 </li>
     * <li> 2. 检验分P数是否在合适范围内 </li>
     * <li> 3. 分支，如果是是新视频，就将视频信息记录和视频文件记录保存在mysql中，并将视频文件全部交给MQ转码 </li>
     * <li> 4. 分支，如果是提交过的视频做修改，如果这个视频没有转码完成或审核完成，就不能继续修改。如果可以修改，那么会上传用户传入的视频文件，删除未出现的视频文件。如果视频文件相同，那么就修改一下序号。 </li>
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
                            String originInfo,
                            List <VideoInfoFileUpload> uploadFileList,
                            TokenUserInfo tokenUserInfo)
    {
        Long userId = tokenUserInfo.getUserInfo().getUserId();
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
        videoInfoUpload.setInteraction(validateInteraction(interaction));
        videoInfoUpload.setOriginInfo(originInfo);

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

            // 最后将图片移动到cover文件夹保存
            // 先校验图片文件路径是否合法且存在
            FileUtil.fileExists(Path.of(webConfig.getRootFilePath(), Constants.FILE_FOLDER_NAME, Constants.TMP_FOLDER_NAME)
                                    .toString(), coverPathStr);
            // 如果移动失败即上传失败
            FileUtil.verifyAndMoveCover(webConfig.getRootFilePath(), coverPathStr);

            videoMqRepository.addVideoFile2TranscodingQueue(uploadFileList);
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
            // 校验本次提交信息是否与旧信息完全一致
            boolean sameVideoInfoUpload = isSameVideoInfoUpload(videoInfoUpload);
            boolean sameVideoFileUpload = verifyDuplicateData(videoId, userId, uploadFileList);
            if (sameVideoInfoUpload && sameVideoFileUpload)
            {
                throw new BusinessException("请勿重复提交");
            }

            Short status = videoInfoUploadDb.getStatus();
            /*
             * 我们的视频处理逻辑是只能修改 审核过的 或者 转码失败的 视频信息和文件，否则就只能等。
             */
            // 不能修改正在转码或未审核的视频信息
            if (status.equals(VideoStatus.TRANSCODING.getStatus()) || status.equals(VideoStatus.PENDING_REVIEW.getStatus()))
            {
                throw new BusinessException("现在不能提交");
            }

            String oldCover = videoInfoUploadDb.getVideoCover();

            /* 处理部分 */
            VideoInfoFileUploadQuery videoFileUploadQuery = new VideoInfoFileUploadQuery();
            videoFileUploadQuery.setVideoId(videoId);
            videoFileUploadQuery.setUserId(userId); // 限制住只能改本用户id的视频，因为是通过token获得用户id，可以避免用户改别人视频
            List <VideoInfoFileUpload> dbUploadFileList = videoInfoFileUploadMapper.selectList(videoFileUploadQuery);

            // 以 uploadId 为 key 建立 DB 文件的查找 Map （不包括转码失败的文件，就算用户携带也不能要）
            Map <Long, VideoInfoFileUpload> dbFileByUploadId = dbUploadFileList.stream()
                                                                               .filter(videoInfoFileUpload -> !videoInfoFileUpload.getTransferResult()
                                                                                                                                  .equals(VideoFileStatus.TRANSCODING_FAIL.getStatus()))
                                                                               .collect(Collectors.toMap(VideoInfoFileUpload::getUploadId,
                                                                                                         Function.identity(),
                                                                                                         (d1, d2) -> d2));
            // 请求中携带的 uploadId 集合，保留的上传文件
            Set <Long> requestUploadIds = uploadFileList.stream()
                                                        .map(VideoInfoFileUpload::getUploadId)
                                                        .collect(Collectors.toSet());

            // 被删除的视频文件：在 DB 中有、但本次请求中没有
            ArrayList <VideoInfoFileUpload> removedFileList = dbUploadFileList.stream()
                                                                              .filter(db -> !requestUploadIds.contains(db.getUploadId()))
                                                                              .collect(Collectors.toCollection(ArrayList::new));

            // 检查视频文件是否都属于自己
            for (VideoInfoFileUpload removedFile : removedFileList)
            {
                if (!Objects.equals(removedFile.getUserId(), userId))
                {
                    throw new BusinessException(ResponseCode.NO_PERMISSION);
                }
            }

            // 对本次请求中已存在于 DB 的文件，回填 DB 数据（fileId、filePath 等），
            // 这样后续 fileId == null 的判断才能正确区分新/旧文件
            for (VideoInfoFileUpload uploadFile : uploadFileList)
            {
                VideoInfoFileUpload dbMatch = dbFileByUploadId.get(uploadFile.getUploadId());
                if (dbMatch != null)
                {
                    uploadFile.setFileId(dbMatch.getFileId());
                    uploadFile.setFilePath(dbMatch.getFilePath());
                    uploadFile.setFileSize(dbMatch.getFileSize());
                    uploadFile.setTransferResult(dbMatch.getTransferResult());
                    uploadFile.setDuration(dbMatch.getDuration());
                    uploadFile.setUpdateType(dbMatch.getUpdateType());
                }
            }

            // 新增的视频文件：在请求中有、但 DB 中没有（回填后 fileId 仍为 null）
            ArrayList <VideoInfoFileUpload> newFileList = new ArrayList <>(uploadFileList.stream()
                                                                                         .filter(file -> file.getFileId() == null)
                                                                                         .toList());

            videoInfoUpload.setLastUpdateTime(curDate);

            boolean isUpdated = !sameVideoInfoUpload;
            // 修改视频状态
            if (!newFileList.isEmpty())
            {
                videoInfoUpload.setStatus(VideoStatus.TRANSCODING.getStatus());
            }
            else if (isUpdated)
            {
                videoInfoUpload.setStatus(VideoStatus.PENDING_REVIEW.getStatus());
            }
            // 更新视频状态
            videoInfoUploadMapper.updateByVideoId(videoInfoUpload, videoId);

            // 删除用户想删除的视频文件
            if (!removedFileList.isEmpty())
            {
                List <Long> fileIdList = removedFileList.stream()
                                                        .map(VideoInfoFileUpload::getFileId)
                                                        .filter(Objects::nonNull)
                                                        .toList();

                // 只在数据库层面删除
                videoInfoFileUploadMapper.deleteBatchByFileId(fileIdList, userId);
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


            // 如果更换封面
            if (!oldCover.equals(coverPathStr))
            {
                // 先把封面移动到video文件夹
                // 先检查一下封面是否合法且存在
                FileUtil.fileExists(Path.of(webConfig.getRootFilePath(),
                                            Constants.FILE_FOLDER_NAME,
                                            Constants.TMP_FOLDER_NAME,
                                            oldCover).toString(), coverPathStr);
                // 如果封面过期了直接修改失败
                FileUtil.verifyAndMoveCover(webConfig.getRootFilePath(), coverPathStr);
                // 然后删除原来的旧封面
                videoMqRepository.addVideoFile2DeleteQueue(Path.of(webConfig.getRootFilePath(),
                                                                   Constants.FILE_FOLDER_NAME,
                                                                   Constants.COVER_FOLDER_NAME,
                                                                   oldCover).toString());
            }

            if (!newFileList.isEmpty())
            {
                for (VideoInfoFileUpload newFile : newFileList)
                {
                    newFile.setUserId(userId);
                    newFile.setVideoId(videoId);
                }
                videoMqRepository.addVideoFile2TranscodingQueue(newFileList);
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
        query.setUserId(tokenUserInfo.getUserInfo().getUserId());
        query.setVideoNameFuzzy(nameFuzzy);
        query.setOrderBy("v.create_time desc");

        if (status != null)
        {
            // 若为-1，则查询未审核的视频，即状态为0、1、2的视频
            if (status == (short) -1)
            {
                query.setExclusiveStatusList(List.of(VideoStatus.REVIEW_SUCCESS.getStatus(),
                                                     VideoStatus.REVIEW_FAILED.getStatus()));
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
    public VideoStatusCountVO getVideoStatusCount(TokenUserInfo tokenUserInfo, String nameFuzzy)
    {
        Long userId = tokenUserInfo.getUserInfo().getUserId();
        VideoInfoUploadQuery query = new VideoInfoUploadQuery();
        query.setUserId(userId);
        query.setVideoNameFuzzy(nameFuzzy);
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
     * 修改视频互动状态<hr/>
     * 可以在任何时候进行此操作，不管是已经通过的视频还是正在上传的视频都会统一更改
     *
     * @param userId      用户ID
     * @param videoId     视频ID
     * @param interaction 互动状态
     */
    @Transactional(rollbackFor = Exception.class)
    public void setInteraction(long userId, long videoId, String interaction)
    {
        String result = validateInteraction(interaction);

        VideoInfo videoInfo = videoInfoMapper.selectByVideoId(videoId);
        // video_info 更新
        if (videoInfo != null && videoInfo.getUserId().equals(userId))
        {
            VideoInfo updatedVideoInfo = new VideoInfo();
            updatedVideoInfo.setInteraction(result);
            videoInfoMapper.updateByVideoId(updatedVideoInfo, videoId);
        }

        VideoInfoUpload videoInfoUpload = videoInfoUploadMapper.selectByVideoId(videoId);
        // video_info_upload 也更新，不管有没有新上传的记录都更新
        if ((videoInfoUpload != null && videoInfoUpload.getUserId().equals(userId)))
        {
            VideoInfoUpload updatedVideoInfoUpload = new VideoInfoUpload();
            updatedVideoInfoUpload.setInteraction(result);
            videoInfoUploadMapper.updateByVideoId(updatedVideoInfoUpload, videoId);
        }
        else
        {
            throw new BusinessException(ResponseCode.NOT_FOUND);
        }
    }

    private String validateInteraction(String interaction)
    {
        if (interaction == null || interaction.isBlank())
        {
            return "";
        }
        List <Integer> list = Arrays.stream(interaction.split(",")).map(String::trim).map(Integer::parseInt).toList();
        if (list.stream().anyMatch(i -> i != 0 && i != 1))
        {
            throw new BusinessException(ResponseCode.NOT_FOUND);
        }
        return list.stream().distinct().sorted().map(String::valueOf).collect(Collectors.joining(","));
    }

    /**
     * 获取上传文件
     *
     * @param videoId 视频ID
     * @param userId  用户ID
     * @return 视频上传文件
     */
    public List <VideoInfoFileUploadVO> loadVideoFileUpload(long videoId, long userId)
    {
        VideoInfoUpload videoInfoUpload = videoInfoUploadMapper.selectByVideoId(videoId);
        if (!Objects.equals(videoInfoUpload.getUserId(), userId))
        {
            throw new BusinessException(ResponseCode.NOT_FOUND);
        }
        List <VideoInfoFileUpload> uploadList = videoInfoFileUploadMapper.selectByVideoId(videoId);
        return uploadList.stream().map(uploadFile ->
                                       {
                                           VideoInfoFileUploadVO vo = new VideoInfoFileUploadVO();
                                           BeanUtils.copyProperties(uploadFile, vo);
                                           return vo;
                                       }).toList();
    }

    /**
     * 用户删除视频
     *
     * @param userId  申请人ID
     * @param videoId 视频ID
     * @param detail  删除详情
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteVideo(long userId, long videoId, String detail)
    {
        VideoInfo videoInfo = videoInfoMapper.selectByVideoId(videoId);
        // 如果 视频不存在 或者 视频不属于该用户
        if (videoInfo == null || !Objects.equals(videoInfo.getUserId(), userId))
        {
            throw new BusinessException(ResponseCode.NOT_FOUND);
        }

        // 给用户扣除发布视频时获得的硬币
        userInfoMapper.decreaseCoin(userId, webConfig.getCoinBonusPerVideo());

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
        videoInfoArchive.setDeleterType(DeleterType.USER.getValue());
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

        // TODO 如果以后要把记录存入redis中，那么这里也要删除redis中留存的记录

        // 删除ES记录
        videoInfoDocRepository.deleteById(videoId);
    }

    public Long getCommentManagementInfoCount(long userId, Long videoId, String nameFuzzy)
    {
        if (nameFuzzy == null || nameFuzzy.isBlank())
        {
            nameFuzzy = null;
        }
        return videoCommentMapper.selectCommentManagementVOCount(userId, videoId, nameFuzzy);
    }

    public List <CommentManagementVO> getCommentManagementInfo(long userId,
                                                               Long videoId,
                                                               String nameFuzzy,
                                                               int pageNo,
                                                               int pageSize)
    {
        if (nameFuzzy == null || nameFuzzy.isBlank())
        {
            nameFuzzy = null;
        }
        int start = (pageNo - 1) * pageSize;
        return videoCommentMapper.selectCommentManagementVO(userId, videoId, nameFuzzy, start, pageSize);
    }

    public Long getDanmakuManagementInfoCount(long userId, Long videoId, Integer fileIndex, String nameFuzzy)
    {
        return videoDanmakuMapper.selectDanmakuManagementVOCount(userId, videoId, fileIndex, nameFuzzy);
    }

    public List <DanmakuManagementVO> getDanmakuManagementInfo(long userId,
                                                               Long videoId,
                                                               Integer fileIndex,
                                                               String nameFuzzy,
                                                               Integer pageNo,
                                                               Integer pageSize)
    {
        int start = (pageNo - 1) * pageSize;
        return videoDanmakuMapper.selectDanmakuManagementVO(userId, videoId, fileIndex, nameFuzzy, start, pageSize);
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
     * 检查视频信息是否相同<hr/>
     * 具体检查：标题、封面、分类、投稿类型、来源、标签、简介、互动设置
     *
     * @param newInfo 用户提交的视频信息
     * @return 若相同，返回true，否则返回false
     */
    private boolean isSameVideoInfoUpload(VideoInfoUpload newInfo)
    {
        VideoInfoUpload dbInfo = videoInfoUploadMapper.selectByVideoId(newInfo.getVideoId());
        if (dbInfo == null)
        {
            return false;
        }
        return Objects.equals(newInfo.getVideoName(), dbInfo.getVideoName()) && Objects.equals(newInfo.getVideoCover(),
                                                                                               dbInfo.getVideoCover()) && Objects.equals(
                newInfo.getPCategoryId(),
                dbInfo.getPCategoryId()) && Objects.equals(newInfo.getCategoryId(), dbInfo.getCategoryId()) && Objects.equals(
                newInfo.getPostType(),
                dbInfo.getPostType()) && Objects.equals(newInfo.getOriginInfo(),
                                                        dbInfo.getOriginInfo()) && Objects.equals(newInfo.getTags(),
                                                                                                  dbInfo.getTags()) && Objects.equals(
                newInfo.getIntroduction(),
                dbInfo.getIntroduction()) && Objects.equals(newInfo.getInteraction(), dbInfo.getInteraction());
    }

    /**
     * 校验视频上传文件数据是否完全相同
     *
     * @return 校验结果，若完全相同即为true
     */
    private boolean verifyDuplicateData(Long videoId, Long userId, List <VideoInfoFileUpload> uploadFileList)
    {
        List <VideoInfoFileUpload> requestFileList = uploadFileList == null ? Collections.emptyList() : uploadFileList;
        VideoInfoFileUploadQuery query = new VideoInfoFileUploadQuery();
        query.setVideoId(videoId);
        query.setUserId(userId);
        query.setOrderBy("v.file_index");
        List <VideoInfoFileUpload> dbFileList = videoInfoFileUploadMapper.selectList(query);
        if (dbFileList == null)
        {
            dbFileList = Collections.emptyList();
        }

        if (requestFileList.size() != dbFileList.size())
        {
            return false;
        }

        for (int i = 0 ; i < requestFileList.size() ; i++)
        {
            VideoInfoFileUpload requestFile = requestFileList.get(i);
            VideoInfoFileUpload dbFile = dbFileList.get(i);
            Long requestUploadId = requestFile.getUploadId();
            Long dbUploadId = dbFile.getUploadId();
            String requestFileName = requestFile.getFileName();
            String dbFileName = dbFile.getFileName();

            // 请求中的文件只携带 uploadId 和 fileName，有任一缺失都不视为重复数据
            if (requestUploadId == null || requestFileName == null || requestFileName.isEmpty())
            {
                return false;
            }

            // uploadId 和 fileName 相同说明既没有修改文件，也没有修改文件名
            if (!Objects.equals(requestUploadId, dbUploadId) || !Objects.equals(requestFileName, dbFileName))
            {
                return false;
            }
        }
        return true;
    }
}
