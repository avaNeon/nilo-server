package com.neon.niloadmin.service;

import com.neon.niloadmin.config.AdminConfig;
import com.neon.niloadmin.mapper.*;
import com.neon.niloadmin.repository.rabbitmq.MqRepository;
import com.neon.nilocommon.entity.constants.Constants;
import com.neon.nilocommon.entity.constants.VideoResolution;
import com.neon.nilocommon.entity.dto.VideoInfoArchiveAdminJoinDTO;
import com.neon.nilocommon.entity.enums.ResponseCode;
import com.neon.nilocommon.entity.po.*;
import com.neon.nilocommon.entity.query.*;
import com.neon.nilocommon.exception.BusinessException;
import com.neon.nilocommon.util.FileUtil;
import com.neon.nilocommon.util.PageCalculator;
import com.neon.nilocommon.util.StringUtil;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Path;
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

    private final VideoCommentArchiveMapper <VideoCommentArchive, VideoCommentArchiveQuery> videoCommentArchiveMapper;

    private final VideoDanmakuArchiveMapper <VideoDanmakuArchive, VideoDanmakuArchiveQuery> videoDanmakuArchiveMapper;

    private final UserCommentActionArchiveMapper <UserCommentActionArchive, UserCommentActionArchiveQuery> userCommentActionArchiveMapper;

    private final UserVideoActionArchiveMapper <UserVideoActionArchive, UserVideoActionArchiveQuery> userVideoActionArchiveMapper;

    private final AdminConfig adminConfig;

    private final MqRepository mqRepository;

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

        String fileRootPath = Path.of(adminConfig.getRootFilePath(), Constants.FILE_FOLDER_NAME).normalize().toString();
        List <String> deletePathList = new ArrayList <>();

        for (Long videoId : videoIdList)
        {
            if (videoId == null)
            {
                continue;
            }

            collectFilePaths(fileRootPath, videoId, deletePathList);
            deleteArchiveRecords(videoId);
        }

        if (!deletePathList.isEmpty())
        {
            try
            {
                mqRepository.addPathList2DeleteQueue(deletePathList.stream().distinct().toList());
            }
            catch (Exception e)
            {
                log.warn("发送删除存档文件消息失败，videoIdList={}，错误信息：{}", videoIdList, e.toString());
            }
        }
    }

    private void collectFilePaths(String fileRootPath, Long videoId, List <String> deletePathList)
    {
        VideoInfoArchive videoInfoArchive = videoInfoArchiveMapper.selectByVideoId(videoId);
        if (videoInfoArchive != null)
        {
            String cover = videoInfoArchive.getVideoCover();
            if (cover != null && !cover.isBlank())
            {
                String coverAbsPath = Path.of(fileRootPath, Constants.COVER_FOLDER_NAME, cover).normalize().toString();
                // 合法性校验，只有合法路径才能传入删除队列
                if (StringUtil.isValidPath(fileRootPath, coverAbsPath) && FileUtil.fileExists(coverAbsPath))
                {
                    deletePathList.add(coverAbsPath);
                }
            }
        }

        VideoInfoFileArchiveQuery fileArchiveQuery = new VideoInfoFileArchiveQuery();
        fileArchiveQuery.setVideoId(videoId);
        List <VideoInfoFileArchive> fileArchiveList = videoInfoFileArchiveMapper.selectList(fileArchiveQuery);
        if (fileArchiveList != null)
        {
            fileArchiveList.stream()
                           .map(VideoInfoFileArchive::getFilePath)
                           .filter(filePath -> filePath != null && !filePath.isBlank())
                           .map(filePath -> Path.of(fileRootPath, filePath).normalize().toString())
                           .filter(absPath -> StringUtil.isValidPath(fileRootPath, absPath))
                           .filter(FileUtil::fileExists)
                           .forEach(deletePathList::add);
        }
    }

    private void deleteArchiveRecords(Long videoId)
    {
        videoInfoArchiveMapper.deleteByVideoId(videoId);

        VideoInfoFileArchiveQuery fileArchiveQuery = new VideoInfoFileArchiveQuery();
        fileArchiveQuery.setVideoId(videoId);
        videoInfoFileArchiveMapper.deleteByParam(fileArchiveQuery);

        VideoCommentArchiveQuery commentArchiveQuery = new VideoCommentArchiveQuery();
        commentArchiveQuery.setVideoId(videoId);
        videoCommentArchiveMapper.deleteByParam(commentArchiveQuery);

        VideoDanmakuArchiveQuery danmakuArchiveQuery = new VideoDanmakuArchiveQuery();
        danmakuArchiveQuery.setVideoId(videoId);
        videoDanmakuArchiveMapper.deleteByParam(danmakuArchiveQuery);

        UserCommentActionArchiveQuery commentActionArchiveQuery = new UserCommentActionArchiveQuery();
        commentActionArchiveQuery.setVideoId(videoId);
        userCommentActionArchiveMapper.deleteByParam(commentActionArchiveQuery);

        UserVideoActionArchiveQuery videoActionArchiveQuery = new UserVideoActionArchiveQuery();
        videoActionArchiveQuery.setVideoId(videoId);
        userVideoActionArchiveMapper.deleteByParam(videoActionArchiveQuery);
    }

    public void downloadVideoMasterM3u8(Long videoId, Integer index, HttpServletResponse response)
    {
        String filePath = queryVideoFileArchiveFilePath(videoId, index);
        readFile(response, filePath + "/" + Constants.MASTER_M3U8_NAME);
    }

    public void downloadVideoPlaylistM3u8(Long videoId, Integer index, Integer resolution, HttpServletResponse response)
    {
        String filePath = queryVideoFileArchiveFilePath(videoId, index);
        String folder = resolveResolutionFolder(resolution);
        readFile(response, filePath + "/" + folder + "/" + Constants.M3U8_NAME);
    }

    public void downloadVideoSegmentTs(Long videoId,
                                       Integer index,
                                       Integer resolution,
                                       String segment,
                                       HttpServletResponse response)
    {
        if (!isValidSegmentName(segment))
        {
            throw new BusinessException(ResponseCode.INVALID_ARGUMENTS);
        }
        String filePath = queryVideoFileArchiveFilePath(videoId, index);
        String folder = resolveResolutionFolder(resolution);
        readFile(response, filePath + "/" + folder + "/" + Constants.TS_FOLDER_NAME + "/" + segment);
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

    private String resolveResolutionFolder(Integer resolution)
    {
        VideoResolution vr = VideoResolution.fromResolution(resolution);
        if (vr == null)
        {
            throw new BusinessException(ResponseCode.INVALID_ARGUMENTS);
        }
        return vr.getFolderName();
    }

    private boolean isValidSegmentName(String segment)
    {
        return segment != null && segment.matches("^\\d{4}\\.ts$");
    }

    private void readFile(HttpServletResponse response, String fileName)
    {
        File file = new File(adminConfig.getRootFilePath() + "/" + Constants.FILE_FOLDER_NAME + "/" + fileName);
        if (!file.exists()) return;
        try (ServletOutputStream outputStream = response.getOutputStream() ; FileInputStream inputStream = new FileInputStream(
                file))
        {
            byte[] bytes = new byte[1024];
            int len;
            while ((len = inputStream.read(bytes)) != -1)
            {
                outputStream.write(bytes, 0, len);
            }
            outputStream.flush();
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
    }
}
