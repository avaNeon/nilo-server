package com.neon.niloadmin.service;

import com.neon.niloadmin.mapper.*;
import com.neon.niloadmin.repository.rabbitmq.MqRepository;
import com.neon.nilocommon.entity.dto.comment.CommentArchiveDTO;
import com.neon.nilocommon.entity.dto.VideoInfoArchiveAdminJoinDTO;
import com.neon.nilocommon.entity.enums.comment.OperationType;
import com.neon.nilocommon.entity.enums.ResponseCode;
import com.neon.nilocommon.entity.po.*;
import com.neon.nilocommon.entity.query.*;
import com.neon.nilocommon.exception.BusinessException;
import com.neon.nilocommon.util.FileUtil;
import com.neon.nilocommon.util.PageCalculator;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@RequiredArgsConstructor
@Service
public class VideoArchiveService
{
    private final VideoInfoArchiveMapper <VideoInfoArchive, VideoInfoArchiveQuery> videoInfoArchiveMapper;

    private final VideoInfoFileArchiveMapper <VideoInfoFileArchive, VideoInfoFileArchiveQuery> videoInfoFileArchiveMapper;

    private final VideoInfoFileMapper <VideoInfoFile, VideoInfoFileQuery> videoInfoFileMapper;

    private final VideoDanmakuArchiveMapper <VideoDanmakuArchive, VideoDanmakuArchiveQuery> videoDanmakuArchiveMapper;

    private final UserVideoActionArchiveMapper <UserVideoActionArchive, UserVideoActionArchiveQuery> userVideoActionArchiveMapper;

    private final MediaOwnershipMapper <MediaOwnership, MediaOwnershipQuery> mediaOwnershipMapper;

    private final MqRepository mqRepository;

    private final FileService fileService;

    /**
     * 分页查询视频存档列表（关联用户信息）
     *
     * @param archiveQuery         查询条件
     * @param orderByDeleteTimeAsc 删除时间排序方向
     * @return 视频存档列表
     */
    public List <VideoInfoArchiveAdminJoinDTO> loadVideoList(VideoInfoArchiveQuery archiveQuery, Boolean orderByDeleteTimeAsc)
    {
        StringBuilder orderBy = new StringBuilder();
        if (orderByDeleteTimeAsc != null)
        {
            orderBy.append("v.delete_time ").append(orderByDeleteTimeAsc ? "asc" : "desc");
        }
        if (orderBy.isEmpty())
        {
            orderBy.append("v.delete_time desc");
        }
        archiveQuery.setOrderBy(orderBy.toString());

        Integer count = videoInfoArchiveMapper.selectCount(archiveQuery);
        archiveQuery.setPageCalculator(new PageCalculator(archiveQuery.getPageNo(), count, archiveQuery.getPageSize()));
        return videoInfoArchiveMapper.selectListWithUserInfo(archiveQuery);
    }

    /**
     * 获取视频存档数量
     *
     * @param archiveQuery 查询条件
     * @return 视频存档数量
     */
    public Integer getVideoArchiveCount(VideoInfoArchiveQuery archiveQuery)
    {
        return videoInfoArchiveMapper.selectCount(archiveQuery);
    }

    /**
     * 查询视频分P信息，优先从存档表获取，若不存在则从正常表获取
     *
     * @param videoId 视频ID
     * @return 分P文件列表
     */
    public List <VideoInfoFileArchive> loadVideoFileList(long videoId)
    {
        VideoInfoFileArchiveQuery archiveQuery = new VideoInfoFileArchiveQuery();
        archiveQuery.setVideoId(videoId);
        archiveQuery.setOrderBy("v.file_index asc");
        List <VideoInfoFileArchive> archiveList = videoInfoFileArchiveMapper.selectList(archiveQuery);
        if (archiveList != null && !archiveList.isEmpty())
        {
            return archiveList;
        }

        VideoInfoFileQuery fileQuery = new VideoInfoFileQuery();
        fileQuery.setVideoId(videoId);
        fileQuery.setOrderBy("v.file_index asc");
        List <VideoInfoFile> fileList = videoInfoFileMapper.selectList(fileQuery);
        if (fileList == null || fileList.isEmpty())
        {
            return List.of();
        }
        return fileList.stream().map(file ->
                                     {
                                         VideoInfoFileArchive archive = new VideoInfoFileArchive();
                                         BeanUtils.copyProperties(file, archive);
                                         return archive;
                                     }).toList();
    }

    /**
     * 批量删除视频存档，包括数据库记录和物理文件
     *
     * @param videoIdList 视频ID列表
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteVideoArchive(List <Long> videoIdList)
    {
        if (videoIdList == null || videoIdList.isEmpty())
        {
            return;
        }

        List <String> videoDeleteKeys = new ArrayList <>();
        List <String> imageDeleteKeys = new ArrayList <>();

        for (Long videoId : videoIdList)
        {
            if (videoId == null)
            {
                continue;
            }

            // 收集封面和缩略图（图片桶）
            VideoInfoArchive videoInfoArchive = videoInfoArchiveMapper.selectByVideoId(videoId);
            if (videoInfoArchive != null)
            {
                String cover = videoInfoArchive.getVideoCover();
                if (cover != null && !cover.isBlank())
                {
                    imageDeleteKeys.add(cover);
                    imageDeleteKeys.add(FileUtil.constructThumbnailName(cover));
                }
            }

            // 收集视频文件 baseKey（视频桶）
            List <VideoInfoFileArchive> fileArchiveList = videoInfoFileArchiveMapper.selectByVideoId(videoId);
            if (fileArchiveList != null)
            {
                fileArchiveList.stream()
                               .map(VideoInfoFileArchive::getFilePath)
                               .filter(filePath -> filePath != null && !filePath.isBlank())
                               .forEach(videoDeleteKeys::add);
            }

            deleteArchiveRecords(videoId);
        }

        // 删除从属表记录
        List <String> allKeys = new ArrayList <>(videoDeleteKeys);
        allKeys.addAll(imageDeleteKeys);
        if (!allKeys.isEmpty())
        {
            mediaOwnershipMapper.deleteBatchByObjectKey(allKeys.stream().distinct().toList());
        }

        // 发送删除消息到MQ
        try
        {
            if (!videoDeleteKeys.isEmpty())
            {
                mqRepository.addKeysToVideoDeleteQueue(videoDeleteKeys.stream().distinct().toList());
            }
            if (!imageDeleteKeys.isEmpty())
            {
                mqRepository.addKeysToImageDeleteQueue(imageDeleteKeys.stream().distinct().toList());
            }
        }
        catch (Exception e)
        {
            log.warn("发送删除存档文件消息失败，videoIdList={}，错误信息：{}", videoIdList, e.toString());
        }
    }

    /**
     * 查询视频存档文件路径（baseKey）<hr/>
     * 供 VideoArchiveController 调用，再交给 FileService 处理 HLS
     */
    public void downloadVideoMasterM3u8(Long videoId, Integer index, HttpServletResponse response)
    {
        String baseKey = queryVideoFileArchiveFilePath(videoId, index);
        fileService.serveMasterM3u8(response, baseKey);
    }

    public void downloadVideoPlaylistM3u8(Long videoId, Integer index, String folder, HttpServletResponse response)
    {
        String baseKey = queryVideoFileArchiveFilePath(videoId, index);
        fileService.servePlaylistM3u8(response, baseKey, folder);
    }

    private String queryVideoFileArchiveFilePath(Long videoId, Integer index)
    {
        VideoInfoFileArchiveQuery archiveQuery = new VideoInfoFileArchiveQuery();
        archiveQuery.setVideoId(videoId);
        archiveQuery.setFileIndex(index);
        List <VideoInfoFileArchive> archiveFiles = videoInfoFileArchiveMapper.selectList(archiveQuery);
        if (archiveFiles == null || archiveFiles.isEmpty() || archiveFiles.get(0).getFilePath() == null)
        {
            throw new BusinessException(ResponseCode.NOT_FOUND);
        }
        return archiveFiles.get(0).getFilePath();
    }

    private void deleteArchiveRecords(Long videoId)
    {
        videoInfoArchiveMapper.deleteByVideoId(videoId);

        VideoInfoFileArchiveQuery fileArchiveQuery = new VideoInfoFileArchiveQuery();
        fileArchiveQuery.setVideoId(videoId);
        videoInfoFileArchiveMapper.deleteByParam(fileArchiveQuery);

        // video_comment + user_comment_action：事务提交后异步通知评论服务彻底删除归档，保证最终一致性
        sendCommentArchiveOperationAfterCommit(new CommentArchiveDTO(videoId, OperationType.DESTROY));

        VideoDanmakuArchiveQuery danmakuArchiveQuery = new VideoDanmakuArchiveQuery();
        danmakuArchiveQuery.setVideoId(videoId);
        videoDanmakuArchiveMapper.deleteByParam(danmakuArchiveQuery);

        UserVideoActionArchiveQuery videoActionArchiveQuery = new UserVideoActionArchiveQuery();
        videoActionArchiveQuery.setVideoId(videoId);
        userVideoActionArchiveMapper.deleteByParam(videoActionArchiveQuery);
    }

    /**
     * 在事务提交后发送视频评论归档/恢复/彻底删除消息<hr/>
     * <p>本地事务未提交成功时绝不会通知评论服务，避免出现"本地回滚但评论已变更"的不一致状态</p>
     */
    private void sendCommentArchiveOperationAfterCommit(CommentArchiveDTO dto)
    {
        if (dto == null)
        {
            return;
        }

        if (!TransactionSynchronizationManager.isSynchronizationActive())
        {
            mqRepository.sendCommentArchiveOperation(dto);
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization()
        {
            @Override
            public void afterCommit()
            {
                mqRepository.sendCommentArchiveOperation(dto);
            }
        });
    }
}
