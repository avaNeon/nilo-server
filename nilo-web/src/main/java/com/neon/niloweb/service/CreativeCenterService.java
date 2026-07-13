package com.neon.niloweb.service;

import cn.hutool.core.lang.Snowflake;
import com.neon.nilocommon.config.SystemConfig;
import com.neon.nilocommon.entity.constants.MinioKey;
import com.neon.nilocommon.entity.dto.comment.CommentArchiveDTO;
import com.neon.nilocommon.entity.dto.VideoInfoUploadJoinDTO;
import com.neon.nilocommon.entity.enums.comment.OperationType;
import com.neon.nilocommon.entity.enums.ResponseCode;
import com.neon.nilocommon.entity.enums.videoInfoArchive.DeleterType;
import com.neon.nilocommon.entity.enums.videoInfoFileUpload.VideoFileStatus;
import com.neon.nilocommon.entity.enums.videoInfoUpload.VideoStatus;
import com.neon.nilocommon.entity.po.*;
import com.neon.nilocommon.entity.po.redis.TokenUserInfo;
import com.neon.nilocommon.entity.query.*;
import com.neon.nilocommon.entity.vo.ResponseVO;
import com.neon.nilocommon.entity.vo.VideoInfoFileUploadVO;
import com.neon.nilocommon.entity.vo.VideoStatusCountVO;
import com.neon.nilocommon.entity.vo.comment.CommentManagementVO;
import com.neon.nilocommon.entity.vo.danmaku.DanmakuManagementVO;
import com.neon.nilocommon.exception.BusinessException;
import com.neon.nilocommon.repository.redis.SystemConfigRedisRepository;
import com.neon.nilocommon.util.FileUtil;
import com.neon.nilocommon.util.PageCalculator;
import com.neon.niloweb.enums.UploadQuotaType;
import com.neon.niloweb.feign.comment.InnerVideoCommentFeignClient;
import com.neon.niloweb.feign.storage.InnerImageFeignClient;
import com.neon.niloweb.feign.storage.InnerVideoFileFeignClient;
import com.neon.niloweb.mapper.*;
import com.neon.niloweb.repository.rabbitmq.CommentMqRepository;
import com.neon.niloweb.repository.rabbitmq.ImageMqRepository;
import com.neon.niloweb.repository.rabbitmq.VideoMqRepository;
import com.neon.niloweb.repository.redis.AccountRedisRepository;
import com.neon.niloweb.repository.redis.CategoryRedisRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
@Service
public class CreativeCenterService
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

    private final CategoryRedisRepository categoryRedisRepository;

    private final SystemConfigRedisRepository systemConfigRedisRepository;

    // ----- other -----

    private final Snowflake snowflake;

    private final VideoMqRepository videoMqRepository;

    private final InnerImageFeignClient innerImageFeignClient;

    private final InnerVideoFileFeignClient innerVideoFileFeignClient;

    private final InnerVideoCommentFeignClient innerVideoCommentFeignClient;

    private final ImageMqRepository imageMqRepository;

    private final CommentMqRepository commentMqRepository;

    private final UploadQuotaService uploadQuotaService;

    /**
     * 视频上传<hr/>
     * 视频上传大致流程：<br/>
     * <ol>
     *     <li> 参数校验、归属权校验 </li>
     *     <li> 整理参数 </li>
     *     <li> 写入DB，转码（如果是修改且没上传新的视频文件就不必转码） </li>
     * </ol>
     * 填写了 VideoInfoFileUpload 的 file_id, user_id, video_id, file_index, update_type, transfer_result 这几个字段
     */
    @Transactional(rollbackFor = Exception.class)
    public void videoUpload(Long videoId,
                            String coverKey,
                            String videoTitle,
                            Integer pCategoryId,
                            Integer categoryId,
                            Short postType,
                            String tags,
                            String introduction,
                            String interaction,
                            String originInfo,
                            List <VideoInfoFileUpload> uploadFileList,
                            List <VideoInfoFileUpload> retainedFileList,
                            List <VideoInfoFileUpload> newFileList,
                            TokenUserInfo tokenUserInfo)
    {
        Long userId = tokenUserInfo.getUserInfo().getUserId();

        if (uploadFileList.isEmpty())
        {
            throw new BusinessException(ResponseCode.NOT_FOUND);
        }

        // 检查分P数是否在合理范围内
        SystemConfig systemConfig = systemConfigRedisRepository.getSystemConfig();
        if (uploadFileList.size() > systemConfig.getVideoMaxEpisodes())
        {
            throw new BusinessException("分P数过多");
        }

        // 将传入的参数赋值给视频信息对象
        VideoInfoUpload videoInfoUpload = new VideoInfoUpload();
        videoInfoUpload.setVideoId(videoId);
        videoInfoUpload.setVideoCover(coverKey);
        videoInfoUpload.setVideoName(videoTitle);
        videoInfoUpload.setUserId(userId);
        videoInfoUpload.setPCategoryId(pCategoryId);
        videoInfoUpload.setCategoryId(categoryId);
        videoInfoUpload.setPostType(postType);
        videoInfoUpload.setTags(tags);
        videoInfoUpload.setIntroduction(introduction);
        videoInfoUpload.setInteraction(validateInteraction(interaction));
        videoInfoUpload.setOriginInfo(originInfo);

        // 先定义一个当前时间，后面用
        LocalDateTime curDate = LocalDateTime.now();

        // 把传入封面key对应地缩略图key先算出来，后面用
        String thumbnailKey = FileUtil.constructThumbnailName(coverKey);

        // 预先获取新封面的key，用于后续返回配额，如果没上传新封面这里就是null
        String newCoverKey = getNewCoverKey(videoId, coverKey);

        boolean refundQuota = false;
        try
        {
            // 新增情况
            if (videoId == null)
            {
                // 不能有保留文件 且 新增视频文件不能为空
                if (!retainedFileList.isEmpty() || newFileList.isEmpty())
                {
                    throw new BusinessException(ResponseCode.INVALID_ARGUMENTS);
                }
                uploadNewVideo(coverKey, userId, newFileList, thumbnailKey, videoInfoUpload, curDate);
            }
            // 修改情况
            else
            {
                modifyVideo(videoId, coverKey, userId, uploadFileList, newFileList, thumbnailKey, videoInfoUpload, curDate);
            }
        }
        catch (BusinessException e)
        {
            throw e;
        }
        catch (Throwable e)
        {
            refundQuota = true;
            throw e;
        }
        finally
        {
            // 主动刷新用户上传视频额度，以免后续转码时把源文件删除导致无法计算额度
            uploadQuotaService.getRemainingQuota(userId, UploadQuotaType.VIDEO);

            if (refundQuota)
            {
                List <String> newVideoFileKeys = newFileList.stream()
                                                            .map(VideoInfoFileUpload::getFilePath)
                                                            .filter(path -> path != null && !path.isBlank())
                                                            .toList();

                refundUploadQuota(userId, newVideoFileKeys, newCoverKey);
            }
        }

    }

    /**
     * 新增视频
     *
     * @param videoInfoUpload 新增视频列表（这里面的视频应该是都过滤好fileId了）
     */
    private void uploadNewVideo(String coverKey,
                                Long userId,
                                List <VideoInfoFileUpload> uploadFileList,
                                String thumbnailKey,
                                VideoInfoUpload videoInfoUpload,
                                LocalDateTime curDate)
    {
        // --- 参数校验 ---

        // 上传视频文件不能为空
        if (uploadFileList == null || uploadFileList.isEmpty())
        {
            throw new BusinessException(ResponseCode.INVALID_ARGUMENTS);
        }

        // 我们已经校验了新上传视频的基本参数，现在校验是否都有key，key是否重复
        validateFileKeys(uploadFileList);

        // --- 校验一下文件归属权 ---

        // 校验图片、缩略图归属权
        Integer totalOwnershipCount = mediaOwnershipMapper.selectCountByObjectKeysAndOwnerIdAndUsed(List.of(coverKey,
                                                                                                            thumbnailKey),
                                                                                                    userId,
                                                                                                    0);

        // 校验文件归属权
        List <String> videoFileKeys = uploadFileList.stream().map(VideoInfoFileUpload::getFilePath).toList();
        totalOwnershipCount += mediaOwnershipMapper.selectCountByObjectKeysAndOwnerIdAndUsed(videoFileKeys, userId, 0);

        // 如果查找不到某些记录，说明可能一些文件不属于该用户，也可能用户复用了某些被使用的文件，总之就是传入参数不合法
        if (videoFileKeys.size() + 2 != totalOwnershipCount)
        {
            throw new BusinessException(ResponseCode.INVALID_ARGUMENTS);
        }

        // --- 新增视频信息写入MySQL ---

        Long videoId = snowflake.nextId();
        videoInfoUpload.setVideoId(videoId);
        videoInfoUpload.setCreateTime(curDate);
        videoInfoUpload.setLastUpdateTime(curDate);
        videoInfoUpload.setStatus(VideoStatus.TRANSCODING.getStatus());
        videoInfoUploadMapper.insert(videoInfoUpload);

        // --- 新增视频文件记录写入MySQL ---
        for (VideoInfoFileUpload uploadFile : uploadFileList)
        {
            uploadFile.setVideoId(videoId);
            uploadFile.setUserId(videoInfoUpload.getUserId());
            uploadFile.setFileId(snowflake.nextId());
            uploadFile.setUpdateType((short) 1);
            uploadFile.setTransferResult(VideoFileStatus.TRANSCODING.getStatus());
        }
        videoInfoFileUploadMapper.insertBatch(uploadFileList);

        // 标记封面和缩略图资源为已使用
        Integer useImageResult = mediaOwnershipMapper.markAsUsed(coverKey, userId, curDate);
        if (useImageResult.equals(0))
        {
            throw new BusinessException("在执行过程中，发现封面资源不存在或已被使用");
        }
        Integer thumbnailResult = mediaOwnershipMapper.markAsUsed(thumbnailKey, userId, curDate);
        if (thumbnailResult.equals(0))
        {
            throw new BusinessException("在执行过程中，发现缩略图资源不存在或已被使用");
        }
        markVideoFilesAsUsed(videoFileKeys, userId, curDate);

        // 移动封面和缩略图key（tmp->pending）
        moveCoverKey(coverKey, thumbnailKey);

        // 移动视频文件的key（tmp->pending）
        moveVideoFileKeys(videoFileKeys);

        // 将新增的视频文件加入转码队列
        addVideoFilesToTranscodingQueueAfterCommit(uploadFileList);
    }

    /**
     * 修改视频
     */
    private void modifyVideo(Long videoId,
                             String coverKey,
                             Long userId,
                             List <VideoInfoFileUpload> uploadFileList,
                             List <VideoInfoFileUpload> newFileList,
                             String thumbnailKey,
                             VideoInfoUpload videoInfoUpload,
                             LocalDateTime curDate)
    {
        // --- 检查部分 ---

        // 修改操作的合法性检查
        VideoInfoUpload dbVideoInfoUpload = videoInfoUploadMapper.selectByVideoId(videoId);
        // 不能修改没有存在的视频信息
        if (dbVideoInfoUpload == null)
        {
            throw new BusinessException("未存储此数据");
        }
        // 不允许修改别人的视频信息
        if (!Objects.equals(dbVideoInfoUpload.getUserId(), userId))
        {
            throw new BusinessException("没有权限修改");
        }

        // 我们的视频处理逻辑是只能修改 审核过的 或者 转码失败的 视频信息和文件，否则就只能等
        Short status = dbVideoInfoUpload.getStatus();
        // 不能修改正在转码或未审核的视频信息
        if (status.equals(VideoStatus.TRANSCODING.getStatus()) || status.equals(VideoStatus.PENDING_REVIEW.getStatus()))
        {
            throw new BusinessException("现在不能提交");
        }
        // 如果审核失败，一个文件都不能保留，必须全部是新上传的文件
        if (VideoStatus.REVIEW_FAILED.getStatus()
                                     .equals(status) && (newFileList.isEmpty() || newFileList.size() != uploadFileList.size()))
        {
            throw new BusinessException("审核失败后请重新上传视频文件");
        }

        // 查出mysql中原有的视频文件
        VideoInfoFileUploadQuery fileUploadQuery = new VideoInfoFileUploadQuery();
        fileUploadQuery.setVideoId(videoId);
        fileUploadQuery.setUserId(userId); // 限制住只能改本用户id的视频
        List <VideoInfoFileUpload> dbFileUploadList = videoInfoFileUploadMapper.selectList(fileUploadQuery);

        // 检查一下是否所有文件都转码失败
        boolean isAllTransferFailed = dbFileUploadList.stream()
                                                      .allMatch(file -> file.getTransferResult()
                                                                            .equals(VideoFileStatus.TRANSCODING_FAIL.getStatus()));

        // 如果全部文件都转码失败，用户必须上传新的文件
        if (VideoStatus.TRANSCODING_FAIL.getStatus().equals(status) && isAllTransferFailed && newFileList.isEmpty())
        {
            throw new BusinessException("请上传新的视频文件");
        }

        // 校验本次提交信息是否与旧信息完全一致
        boolean sameVideoInfoUpload = verifySameVideoInfo(videoInfoUpload);

        boolean sameVideoFileUpload = verifySameFiles(videoId, userId, uploadFileList);

        if (sameVideoInfoUpload && sameVideoFileUpload)
        {
            throw new BusinessException("请勿重复提交");
        }

        /* --- 处理部分 --- */

        // 以 fileId 为 key 建立 DB 文件的查找 Map （不包括转码失败的文件，就算用户携带也不能要）（路径为空的文件也会被排除）
        Map <Long, VideoInfoFileUpload> dbFileByFileId = dbFileUploadList.stream()
                                                                         .filter(videoInfoFileUpload -> !VideoFileStatus.TRANSCODING_FAIL.getStatus()
                                                                                                                                         .equals(videoInfoFileUpload.getTransferResult())) // 排除转码失败的文件
                                                                         .filter(videoInfoFileUpload -> videoInfoFileUpload.getFilePath() != null && !videoInfoFileUpload.getFilePath()
                                                                                                                                                                         .isBlank()) // 排除路径为空的文件
                                                                         .filter(videoInfoFileUpload -> videoInfoFileUpload.getFileId() != null)
                                                                         .collect(Collectors.toMap(VideoInfoFileUpload::getFileId,
                                                                                                   Function.identity(),
                                                                                                   (d1, d2) -> d2));
        // 请求中携带的 fileId 集合，保留的上传文件
        Set <Long> reservedFileIdSet = new HashSet <>();

        // 对本次请求中已存在于 DB 的文件，回填 DB 数据（fileId、filePath 等）
        for (VideoInfoFileUpload uploadFile : uploadFileList)
        {
            Long fileId = uploadFile.getFileId();
            if (fileId != null)
            {
                // 不能出现重复的fileId
                if (!reservedFileIdSet.add(fileId))
                {
                    throw new BusinessException(ResponseCode.INVALID_ARGUMENTS);
                }

                VideoInfoFileUpload dbMatch = dbFileByFileId.get(fileId);
                if (dbMatch != null)
                {
                    uploadFile.setFileId(dbMatch.getFileId());
                    uploadFile.setFilePath(dbMatch.getFilePath());
                    uploadFile.setFileSize(dbMatch.getFileSize());
                    uploadFile.setTransferResult(dbMatch.getTransferResult());
                    uploadFile.setDuration(dbMatch.getDuration());
                    // 这个 update_type 字段必须严格满足只有新视频文件才是 update_type 是1，老文件是0，否则转码会把所有 update_type=1的文件上传
                    uploadFile.setUpdateType(dbMatch.getUpdateType());
                }
                else
                {
                    // 如果传了不合法的文件，例如转码失败的文件，这一步也会抛出异常
                    // 使用了不存在的fileId，直接报错
                    throw new BusinessException(ResponseCode.INVALID_ARGUMENTS);
                }
            }
        }

        // 被删除的视频文件：在 DB 中有、但本次请求中没有
        ArrayList <VideoInfoFileUpload> removedFileList = dbFileUploadList.stream()
                                                                          .filter(db -> !reservedFileIdSet.contains(db.getFileId()))
                                                                          .collect(Collectors.toCollection(ArrayList::new));

        videoInfoUpload.setLastUpdateTime(curDate);

        // 如果有新增的文件
        if (!newFileList.isEmpty())
        {
            // 设置好状态
            videoInfoUpload.setStatus(VideoStatus.TRANSCODING.getStatus());

            // 在从属表中启用
            markVideoFilesAsUsed(newFileList.stream().map(VideoInfoFileUpload::getFilePath).toList(), userId, curDate);
        }
        // 否则视频信息就直接设置成待审核状态即可，因为没有新增的视频文件，也就没有转码这一步
        else
        {
            videoInfoUpload.setStatus(VideoStatus.PENDING_REVIEW.getStatus());
        }

        // 更新视频状态到MySQL中
        videoInfoUploadMapper.updateByVideoId(videoInfoUpload, videoId);

        // 删除用户想删除的视频文件记录
        if (!removedFileList.isEmpty())
        {
            List <Long> fileIdList = removedFileList.stream()
                                                    .map(VideoInfoFileUpload::getFileId)
                                                    .filter(Objects::nonNull)
                                                    .toList();

            // 先只在数据库层面删除，删除具体文件的操作在审核通过阶段做
            // 这主要是防止公开的资源在审核前就被删除，导致不可用
            if (!fileIdList.isEmpty())
            {
                // 我们在这里删除了记录，到时候和公开的视频文件一对比，少了哪个文件就会自动删除哪个文件
                videoInfoFileUploadMapper.deleteBatchByFileId(fileIdList, userId);

                // 但是对于不会到公开那一步的资源，我们现在就要删除
                // 审核失败时，会删除所有新上传的文件，这个不用在此处理
                // 对于转码文件，在consumer中就会被删除，也无需额外处理
            }
        }

        // 更新视频文件记录到MySQL中
        for (VideoInfoFileUpload uploadFile : uploadFileList)
        {
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
        String oldCoverKey = dbVideoInfoUpload.getVideoCover();
        boolean coverChanged = !Objects.equals(oldCoverKey, coverKey);
        if (coverChanged)
        {
            // 校验一下文件归属权（图片和缩略图都要校验）
            Integer count = mediaOwnershipMapper.selectCountByObjectKeysAndOwnerIdAndUsed(List.of(coverKey, thumbnailKey),
                                                                                          userId,
                                                                                          0);
            if (!count.equals(2))
            {
                throw new BusinessException(ResponseCode.INVALID_ARGUMENTS);
            }

            // 标记封面和缩略图资源为已使用
            mediaOwnershipMapper.markAsUsedBatch(List.of(coverKey, thumbnailKey), userId, curDate);

            // 把新的封面从tmp移动到pending
            moveCoverKey(coverKey, thumbnailKey);
        }

        if (!newFileList.isEmpty())
        {
            // 将新增的视频文件从tmp移动到pending
            moveVideoFileKeys(newFileList.stream().map(VideoInfoFileUpload::getFilePath).toList());
            // 将新增的视频文件加入转码队列
            addVideoFilesToTranscodingQueueAfterCommit(newFileList);
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

        List <VideoInfoUploadJoinDTO> result = videoInfoUploadMapper.selectListWithVideoInfo(query);

        // 从Redis获取分类ID到编号的映射，回填视频的分类编号
        List <CategoryInfo> categoryInfoList = categoryRedisRepository.getCategoryInfo();

        if (categoryInfoList != null && !categoryInfoList.isEmpty())
        {
            // ID 到 NUM 的映射
            Map <Integer, String> idToNumber = categoryInfoList.stream()
                                                               .collect(Collectors.toMap(CategoryInfo::getCategoryId,
                                                                                         CategoryInfo::getCategoryNumber,
                                                                                         (a, b) -> a));

            for (VideoInfoUploadJoinDTO dto : result)
            {
                if ((dto.getParentCategoryNumber() == null || dto.getParentCategoryNumber()
                                                                 .isBlank()) && dto.getPCategoryId() != null)
                {
                    dto.setParentCategoryNumber(idToNumber.get(dto.getPCategoryId()));
                }
                if ((dto.getCategoryNumber() == null || dto.getCategoryNumber().isBlank()) && dto.getCategoryId() != null)
                {
                    dto.setCategoryNumber(idToNumber.get(dto.getCategoryId()));
                }
            }
        }

        return result;
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

        // 如果没找到视频上传记录，就抛异常
        if (videoInfoUpload == null || !Objects.equals(videoInfoUpload.getUserId(), userId))
        {
            throw new BusinessException(ResponseCode.NOT_FOUND);
        }

        List <VideoInfoFileUpload> uploadList = videoInfoFileUploadMapper.selectByVideoId(videoId);

        boolean transcodingFailed = VideoStatus.TRANSCODING_FAIL.getStatus().equals(videoInfoUpload.getStatus());

        return uploadList.stream().map(uploadFile ->
                                       {
                                           VideoInfoFileUploadVO vo = new VideoInfoFileUploadVO();
                                           BeanUtils.copyProperties(uploadFile, vo);

                                           // 转码失败时仅空路径文件不可复用
                                           if (transcodingFailed && (uploadFile.getFilePath() == null || uploadFile.getFilePath()
                                                                                                                   .isBlank()))
                                           {
                                               vo.setFileId(null);
                                           }

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

        // 如果正式表没有记录，转到删除未发布视频方法
        if (videoInfo == null)
        {
            // 限定用户ID查一下记录
            VideoInfoUpload videoInfoUpload = videoInfoUploadMapper.selectByVideoIdAndUserId(videoId, userId);

            // 校验一下记录是否存在
            if (videoInfoUpload != null)
            {
                // 只能是 转码失败/待审核/审核失败 这几个状态
                if (!VideoStatus.TRANSCODING_FAIL.getStatus()
                                                 .equals(videoInfoUpload.getStatus()) && !VideoStatus.PENDING_REVIEW.getStatus()
                                                                                                                    .equals(videoInfoUpload.getStatus()) && !VideoStatus.REVIEW_FAILED.getStatus()
                                                                                                                                                                                      .equals(videoInfoUpload.getStatus()))
                {
                    throw new BusinessException(ResponseCode.NOT_FOUND);
                }

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
        }
        else
        {
            // 如果 视频不属于该用户
            if (!Objects.equals(videoInfo.getUserId(), userId))
            {
                throw new BusinessException(ResponseCode.NOT_FOUND);
            }

            // 给用户扣除发布视频时获得的硬币
            userInfoMapper.decreaseCoinForVideoDelete(userId,
                                                      systemConfigRedisRepository.getSystemConfig().getRewardsPreUpload());

            // 迁移之前把改查的记录查出来
            List <VideoInfoFile> videoInfoFiles = videoInfoFileMapper.selectByVideoId(videoId);

            // 数据迁移和删除
            archiveVideo(userId, videoId, detail, videoInfo);

            // 移动封面和缩略图
            moveImage(MinioKey.PUBLIC_PREFIX, MinioKey.PENDING_PREFIX, videoInfo.getVideoCover());

            // 移动视频文件
            List <String> videoBaseKeyList = videoInfoFiles.stream().map(VideoInfoFile::getFilePath).toList();
            moveVideoFiles(MinioKey.PUBLIC_PREFIX, MinioKey.PENDING_PREFIX, videoBaseKeyList);
        }
    }

    public Long getCommentManagementInfoCount(long userId, Long videoId, String nameFuzzy)
    {
        if (nameFuzzy == null || nameFuzzy.isBlank())
        {
            nameFuzzy = null;
        }
        ResponseVO <Long> result = innerVideoCommentFeignClient.getCreatorCommentManagementInfoCount(userId, videoId, nameFuzzy);
        assertCommentFeignSuccess(result);
        return result.getData();
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
        ResponseVO <List <CommentManagementVO>> result =
                innerVideoCommentFeignClient.getCreatorCommentManagementInfo(pageNo, pageSize, userId, videoId, nameFuzzy);
        assertCommentFeignSuccess(result);
        return result.getData();
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
     * 校验视频文件的key<hr/>
     * <ul>
     *     <li>任何文件对应的key都不能为空</li>
     *     <li>任何文件的key都不能重复</li>
     * </ul>
     *
     * @param uploadFileList 视频文件列表
     */
    private void validateFileKeys(List <VideoInfoFileUpload> uploadFileList)
    {
        // TODO 可能有点多余
        // 非空才校验
        if (uploadFileList != null && !uploadFileList.isEmpty())
        {
            List <String> videoFileKeys = new ArrayList <>(uploadFileList.size());
            for (VideoInfoFileUpload uploadFile : uploadFileList)
            {
                String filePath = uploadFile.getFilePath();
                if (filePath == null || filePath.isBlank())
                {
                    throw new BusinessException(ResponseCode.INVALID_ARGUMENTS);
                }
                videoFileKeys.add(filePath);
            }

            if (new HashSet <>(videoFileKeys).size() != videoFileKeys.size())
            {
                throw new BusinessException(ResponseCode.INVALID_ARGUMENTS);
            }
        }
    }

    /**
     * 批量将文件在从属表中标记位已使用
     *
     * @param videoFileKeys 视频文件key列表
     * @param userId        用户id
     * @param dateTime      时间
     * @throws BusinessException 当传入文件key在从属表 不存在/不属于当前用户/已经启用 时，会抛出业务异常
     */
    private void markVideoFilesAsUsed(List <String> videoFileKeys, Long userId, LocalDateTime dateTime)
    {
        if (videoFileKeys == null || videoFileKeys.isEmpty())
        {
            return;
        }

        Integer count = mediaOwnershipMapper.markAsUsedBatch(videoFileKeys, userId, dateTime);
        if (!Objects.equals(count, videoFileKeys.size()))
        {
            throw new BusinessException(ResponseCode.INVALID_ARGUMENTS);
        }
    }

    /**
     * 批量移动封面和缩略图key（tmp->pending）
     *
     * @param coverKey     封面key
     * @param thumbnailKey 缩略图key
     */
    private void moveCoverKey(String coverKey, String thumbnailKey)
    {
        // 将图片和缩略图从tmp批量移动到pending
        Map <String, String> keyMap = Map.of(MinioKey.TMP_PREFIX + coverKey,
                                             MinioKey.PENDING_PREFIX + coverKey,
                                             MinioKey.TMP_PREFIX + thumbnailKey,
                                             MinioKey.PENDING_PREFIX + thumbnailKey);

        ResponseVO <Void> result = innerImageFeignClient.batchMove(keyMap);
        if (!result.getCode().equals(ResponseCode.SUCCESS.getCode()))
        {
            throw new RuntimeException("图片移动失败");
        }
    }

    /**
     * 批量移动视频文件key（tmp->pending）
     *
     * @param videoFileKeys 视频文件key列表
     */
    private void moveVideoFileKeys(List <String> videoFileKeys)
    {
        // 组装好map
        Map <String, String> keyMap = new HashMap <>(videoFileKeys.size());
        for (String key : videoFileKeys)
        {
            keyMap.put(MinioKey.TMP_PREFIX + key, MinioKey.PENDING_PREFIX + key);
        }

        // 传给储存微服务
        ResponseVO <Void> result = innerVideoFileFeignClient.batchMove(keyMap);
        if (!result.getCode().equals(ResponseCode.SUCCESS.getCode()))
        {
            throw new RuntimeException("视频文件移动失败");
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
     * 将视频文件列表在事务提交后传给MQ
     *
     * @param uploadFileList 上传文件列表
     */
    private void addVideoFilesToTranscodingQueueAfterCommit(List <VideoInfoFileUpload> uploadFileList)
    {
        if (uploadFileList == null || uploadFileList.isEmpty())
        {
            return;
        }

        // （以防万一）如果事务已经结束，就直接发送到转码队列
        if (!TransactionSynchronizationManager.isSynchronizationActive())
        {
            videoMqRepository.addVideoFilesToTranscodingQueue(uploadFileList);
            return;
        }

        // 在事务提交之后再把文件发送到转码队列
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization()
        {
            @Override
            public void afterCommit()
            {
                videoMqRepository.addVideoFilesToTranscodingQueue(uploadFileList);
            }
        });
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

    /**
     * 校验互动设置是否合法，并尽量修复
     *
     * @param interaction 互动设置字符串
     * @return 如果有重复或者顺序错误的，可以返回正确格式
     */
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
     * 检查视频信息是否相同<hr/>
     * 具体检查：标题、封面、分类、投稿类型、来源、标签、简介、互动设置
     *
     * @param newInfo 用户提交的视频信息
     * @return 若相同，返回true，否则返回false
     */
    private boolean verifySameVideoInfo(VideoInfoUpload newInfo)
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
     * 校验视频上传文件数据是否完全相同<hr/>
     * <p>视频文件只能上传三个字段：</p>
     * <ol>
     *     <li>文件名</li>
     *     <li>文件路径（key）</li>
     *     <li>文件ID</li>
     * </ol>
     * <p>如果这三个都一致说明数据相同</p>
     *
     * @return 校验结果，若完全相同即为true
     */
    private boolean verifySameFiles(Long videoId, Long userId, List <VideoInfoFileUpload> uploadFileList)
    {
        List <VideoInfoFileUpload> requestFileList = uploadFileList == null ? Collections.emptyList() : uploadFileList;

        // 先把mysql中数据取出
        VideoInfoFileUploadQuery query = new VideoInfoFileUploadQuery();
        query.setVideoId(videoId);
        query.setUserId(userId);
        query.setOrderBy("v.file_index");
        List <VideoInfoFileUpload> dbFileList = videoInfoFileUploadMapper.selectList(query);
        if (dbFileList == null)
        {
            dbFileList = Collections.emptyList();
        }

        // 先比较数量
        if (requestFileList.size() != dbFileList.size())
        {
            return false;
        }

        // 挨个文件比较
        for (int i = 0 ; i < requestFileList.size() ; i++)
        {
            VideoInfoFileUpload requestFile = requestFileList.get(i);
            VideoInfoFileUpload dbFile = dbFileList.get(i);

            // 校验fileId是否相同，如果相同，那么就绝对是相同的文件
            // 如果这里是新上传的文件，就不会有fileId，绝对不同
            Long requestFileId = requestFile.getFileId();
            Long dbFileId = dbFile.getFileId();
            // 校验有没有改名
            String requestFileName = requestFile.getFileName();
            String dbFileName = dbFile.getFileName();
            // 校验排序是否一致
            Integer requestFileIndex = requestFile.getFileIndex();
            Integer dbFileIndex = dbFile.getFileIndex();

            // 三个参数都校验一下
            if (!Objects.equals(requestFileName, dbFileName) || !Objects.equals(requestFileId, dbFileId) || !Objects.equals(
                    requestFileIndex,
                    dbFileIndex))
            {
                return false;
            }
        }
        return true;
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
     * 判断本次提交是否包含新封面，并获取新封面（用于非业务异常时返还图片上传额度）
     */
    private String getNewCoverKey(Long videoId, String coverKey)
    {
        if (coverKey == null || coverKey.isBlank())
        {
            return null;
        }

        if (videoId == null)
        {
            return coverKey;
        }

        VideoInfoUpload existingUpload = videoInfoUploadMapper.selectByVideoId(videoId);
        if (existingUpload != null && !Objects.equals(existingUpload.getVideoCover(), coverKey))
        {
            return coverKey;
        }

        return null;
    }

    /**
     * 非业务异常时返还本次提交涉及的新文件上传额度
     */
    private void refundUploadQuota(long userId, List <String> newVideoFileKeys, String newCoverKey)
    {
        if (newVideoFileKeys != null)
        {
            for (String videoFileKey : newVideoFileKeys)
            {
                try
                {
                    long fileSize = probeVideoFileSize(videoFileKey);
                    if (fileSize <= 0)
                    {
                        continue;
                    }

                    LocalDate quotaDate = resolveQuotaDate(videoFileKey);
                    uploadQuotaService.releaseForDate(userId, UploadQuotaType.VIDEO, fileSize, quotaDate);
                }
                catch (Exception e)
                {
                    log.warn("返还视频上传额度失败, userId={}, key={}", userId, videoFileKey, e);
                }
            }
        }

        // 如果没上传新封面，在此返回
        if (newCoverKey == null || newCoverKey.isBlank())
        {
            return;
        }

        try
        {
            long coverSize = probeImageFileSize(newCoverKey);
            if (coverSize <= 0)
            {
                return;
            }

            LocalDate quotaDate = resolveQuotaDate(newCoverKey);

            uploadQuotaService.releaseForDate(userId, UploadQuotaType.IMAGE, coverSize, quotaDate);
        }
        catch (Exception e)
        {
            log.warn("返还图片上传额度失败, userId={}, key={}", userId, newCoverKey, e);
        }
    }

    private long probeVideoFileSize(String baseKey)
    {
        ResponseVO <Long> result = innerVideoFileFeignClient.probeVideoFileSize(baseKey);
        if (result != null && ResponseCode.SUCCESS.getCode().equals(result.getCode()) && result.getData() != null)
        {
            return result.getData();
        }
        return 0L;
    }

    private long probeImageFileSize(String baseKey)
    {
        for (String prefix : List.of(MinioKey.TMP_PREFIX, MinioKey.PENDING_PREFIX, MinioKey.PUBLIC_PREFIX))
        {
            try
            {
                ResponseVO <Long> result = innerImageFeignClient.getImageSize(prefix + baseKey);
                if (result != null && ResponseCode.SUCCESS.getCode()
                                                          .equals(result.getCode()) && result.getData() != null && result.getData() > 0)
                {
                    return result.getData();
                }
            }
            catch (Exception ignored)
            {
                // 继续尝试下一个前缀
            }
        }
        return 0L;
    }

    private LocalDate resolveQuotaDate(String objectKey)
    {
        MediaOwnership ownership = mediaOwnershipMapper.selectByObjectKey(objectKey);
        if (ownership != null && ownership.getCreatedTime() != null)
        {
            return ownership.getCreatedTime().toLocalDate();
        }
        return LocalDate.now();
    }

    private void assertCommentFeignSuccess(ResponseVO <?> result)
    {
        if (result == null || !ResponseCode.SUCCESS.getCode().equals(result.getCode()))
        {
            throw new BusinessException(result == null ? "评论服务调用失败" : result.getInfo());
        }
    }
}
