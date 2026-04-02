package com.neon.niloweb.service;


import cn.hutool.core.lang.Snowflake;
import com.neon.nilocommon.entity.constants.Constants;
import com.neon.nilocommon.entity.constants.DatePattern;
import com.neon.nilocommon.entity.dto.TokenUserInfo;
import com.neon.nilocommon.entity.dto.UploadedVideoFileDTO;
import com.neon.nilocommon.entity.enums.ResponseCode;
import com.neon.nilocommon.entity.po.VideoInfoFile;
import com.neon.nilocommon.entity.query.VideoInfoFileQuery;
import com.neon.nilocommon.exception.BusinessException;
import com.neon.nilocommon.util.FFmpegUtil;
import com.neon.nilocommon.util.FileUtil;
import com.neon.nilocommon.util.StringUtil;
import com.neon.niloweb.config.SystemConfig;
import com.neon.niloweb.config.WebConfig;
import com.neon.niloweb.mapper.VideoInfoFileMapper;
import com.neon.niloweb.repository.redis.UploadRedisRepository;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.RandomStringUtils;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@RequiredArgsConstructor
@Service
public class FileService
{
    private final WebConfig webConfig;

    private final SystemConfig systemConfig;

    private final Snowflake snowflake;

    private final UploadRedisRepository uploadRedisRepository;

    private final VideoInfoFileMapper <VideoInfoFile, VideoInfoFileQuery> videoInfoFileMapper;

    /**
     * 上传视频封面（分类的封面和视频封面都是放在file/cover下的，但是视频的cover按天保存，分类的cover按月保存）<hr/>
     * 保存路径：/root/file/cover
     *
     * @param file            图片文件
     * @param createThumbnail 是否创建缩略图
     * @return 文件相对路径（工作路径为 &lt;项目路径/file&gt;）
     */
    public String uploadImage(MultipartFile file, Boolean createThumbnail)
    {
        String dateName = LocalDate.now().format(DateTimeFormatter.ofPattern(DatePattern.DATE)); // 目录按日划分
        String folderPath = webConfig.getRootFilePath() + "/" + Constants.FILE_FOLDER_NAME + "/" + Constants.COVER_FOLDER_NAME + "/" + dateName;

        File folderFile = new File(folderPath);
        if (!folderFile.exists()) folderFile.mkdirs();

        String fileName = file.getOriginalFilename();
        String suffix = StringUtil.getSuffix(fileName);
        String savedFileName = RandomStringUtils.randomAlphanumeric(30) + suffix; // 给上传的视频封面改名

        String filePath = folderPath + "/" + savedFileName; // 将文件拷贝至指定位置
        try
        {
            file.transferTo(new File(filePath));
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }

        // 生成缩略图
        if (createThumbnail)
        {
            FFmpegUtil.creatImgThumbnail(filePath, false);
        }

        return dateName + "/" + savedFileName;
    }

    /**
     * 下载图片
     *
     * @param response HttpServletResponse
     * @param filePath 文件路径
     */
    public void downloadImage(HttpServletResponse response, String filePath)
    {
        String coverRootPath = Paths.get(webConfig.getRootFilePath(), Constants.FILE_FOLDER_NAME, Constants.COVER_FOLDER_NAME).toString();
        String absolutePath = Paths.get(coverRootPath, filePath).toString();
        if (!StringUtil.isValidPath(absolutePath, coverRootPath)) throw new BusinessException("非法的文件路径");
        String suffix = StringUtil.getSuffix(filePath);
        response.setContentType(resolveImageContentType(suffix));
        response.setHeader("Cache-Control", "max-age=2592000"); // 30天
        readFile(response, Constants.COVER_FOLDER_NAME + "/" + filePath);
    }

    /**
     * 预上传视频<hr/>
     * 文件路径：/&lt;root&gt;/file/tmp
     *
     * @param fileName      文件名
     * @param chunkSize     （视频文件）分块大小
     * @param tokenUserInfo 用户信息DTO（带token）
     * @return uploadId
     */
    public Long preUploadVideo(String fileName, Integer chunkSize, TokenUserInfo tokenUserInfo)
    {
        UploadedVideoFileDTO video = new UploadedVideoFileDTO();
        Long uploadId = snowflake.nextId();
        video.setUploadId(uploadId);
        video.setFileName(fileName);
        video.setChunkSize(chunkSize);
        video.setChunkIndex(0); // 设置初始的chunkIndex
        uploadRedisRepository.addPreUploadKey(video, tokenUserInfo.getUserInfo().getUserId());
        return uploadId;
    }

    /**
     * 上传视频（的一块）
     *
     * @param chunkFile  单个分块视频文件
     * @param chunkIndex 分块索引
     * @param userId     用户id
     * @param uploadId   上传id
     */
    public void uploadVideo(MultipartFile chunkFile, int chunkIndex, long userId, long uploadId)
    {
        UploadedVideoFileDTO videoFileDTO = uploadRedisRepository.getPreUploadKey(userId, uploadId);
        if (videoFileDTO == null) throw new BusinessException("文件不存在，请重新上传");
        // 查看视频文件是否超过限制
        if (videoFileDTO.getFileSize() + chunkFile.getSize() > systemConfig.getVideoMaxSize() * Constants.Mebibyte)
        {
            throw new BusinessException("文件大小超过限制");
        }
        // 块号必须是：≥1，上一个块号+1，并且不能超过总块数
        if (chunkIndex < 1 || chunkIndex != videoFileDTO.getChunkIndex() + 1 || chunkIndex > videoFileDTO.getChunkSize())
        {
            throw new BusinessException(ResponseCode.INVALID_ARGUMENTS);
        }

        File targetFile = new File(webConfig.getRootFilePath() + "/" + Constants.FILE_FOLDER_NAME + "/" + Constants.TMP_FOLDER_NAME + "/" + videoFileDTO.getFilePath() + "/" + chunkIndex);
        try
        {
            chunkFile.transferTo(targetFile);
            videoFileDTO.setChunkIndex(chunkIndex); // 更新了chunkIndex信息
            videoFileDTO.addFileSize(chunkFile.getSize());
            // 更新Redis中存储的视频信息
            uploadRedisRepository.updatePreUploadKey(videoFileDTO, userId);
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
    }

    /**
     * 删除上传的视频文件
     *
     * @param uploadId 上传id
     * @param userId   用户id
     */
    public void deleteVideo(long uploadId, long userId)
    {
        UploadedVideoFileDTO fileDTO = uploadRedisRepository.getPreUploadKey(userId, uploadId);
        if (fileDTO == null)
        {
            throw new BusinessException("所要删除的文件不存在");
        }
        uploadRedisRepository.deletePreUploadKey(userId, uploadId);
        FileUtil.deleteFolder(new File(webConfig.getRootFilePath() + "/" + Constants.FILE_FOLDER_NAME + "/" + Constants.TMP_FOLDER_NAME + "/" + fileDTO.getFilePath()));
    }

    /**
     * 获取index.m3u8
     *
     * @param videoId  视频ID
     * @param index    分P索引
     * @param response HttpServletResponse
     */
    public void downloadVideoResourceM3u8(Long videoId, Integer index, HttpServletResponse response)
    {
        VideoInfoFileQuery infoFileQuery = new VideoInfoFileQuery();
        infoFileQuery.setVideoId(videoId);
        infoFileQuery.setFileIndex(index);
        List <VideoInfoFile> videoInfoFiles = videoInfoFileMapper.selectList(infoFileQuery);
        // 这里获取的应该是一个文件
        if (videoInfoFiles == null || videoInfoFiles.size() != 1)
        {
            throw new BusinessException(ResponseCode.NOT_FOUND);
        }
        VideoInfoFile infoFile = videoInfoFiles.get(0);
        String filePath = infoFile.getFilePath();
        // todo 更新视频的播放信息（例如：播放量）
        readFile(response, filePath + "/" + Constants.M3U8_NAME);
    }

    /**
     * 获取ts文件
     *
     * @param videoId   视频ID
     * @param index     分P索引
     * @param tsPathStr ts文件路径
     * @param response  HttpServletResponse
     */
    public void downloadVideoResourceTs(Long videoId, Integer index, String tsPathStr, HttpServletResponse response)
    {
        VideoInfoFileQuery infoFileQuery = new VideoInfoFileQuery();
        infoFileQuery.setVideoId(videoId);
        infoFileQuery.setFileIndex(index);
        List <VideoInfoFile> videoInfoFiles = videoInfoFileMapper.selectList(infoFileQuery);
        // 这里获取的应该是一个文件
        if (videoInfoFiles == null || videoInfoFiles.size() != 1)
        {
            throw new BusinessException(ResponseCode.NOT_FOUND);
        }
        VideoInfoFile infoFile = videoInfoFiles.get(0);
        String filePath = infoFile.getFilePath();
        // todo 更新视频的播放信息（例如：播放量）
        readFile(response, filePath + "/" + Constants.TS_FOLDER_NAME + "/" + tsPathStr);
    }

    /**
     * 从系统中读取文件，写出到response中
     *
     * @param response HttpServletResponse
     * @param filePath 相对于 根路径/file 下的文件路径
     */
    private void readFile(HttpServletResponse response, String filePath)
    {
        Path rootPath = Paths.get(webConfig.getRootFilePath(), Constants.FILE_FOLDER_NAME).normalize();
        Path targetPath = rootPath.resolve(filePath).normalize();

        if (!StringUtil.isValidPath(targetPath.toString(), rootPath.toString()))
        {
            throw new BusinessException("非法的文件路径");
        }

        if (!Files.exists(targetPath) || !Files.isRegularFile(targetPath))
        {
            throw new BusinessException(ResponseCode.NOT_FOUND);
        }

        // 如果外部没有提前设置 contentType ，这里按后缀补齐
        if (response.getContentType() == null)
        {
            response.setContentType(resolveContentTypeBySuffix(filePath));
        }

        String fileName = targetPath.getFileName() == null ? "file" : targetPath.getFileName().toString();
        response.setHeader("Content-Disposition", "inline; filename=\"" + fileName + "\"");

        try (ServletOutputStream outputStream = response.getOutputStream())
        {
            Files.copy(targetPath, outputStream);
            outputStream.flush();
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
    }

    /**
     * 将图像格式转换为对应的格式
     *
     * @param suffix 图像格式后缀名
     * @return 转换后的格式名
     */
    private String resolveImageContentType(String suffix)
    {
        if (suffix == null) return "application/octet-stream";
        return switch (suffix.toLowerCase())
        {
            case ".jpg", ".jpeg" -> "image/jpeg";
            case ".png" -> "image/png";
            case ".gif" -> "image/gif";
            case ".webp" -> "image/webp";
            case ".avif" -> "image/avif";
            case ".svg" -> "image/svg+xml";
            default -> "application/octet-stream";
        };
    }

    /**
     * 根据文件路径后缀推断响应 Content-Type
     */
    private String resolveContentTypeBySuffix(String filePath)
    {
        String suffix = StringUtil.getSuffix(filePath);
        if (suffix == null)
        {
            return "application/octet-stream";
        }

        return switch (suffix.toLowerCase())
        {
            case ".m3u8" -> "application/vnd.apple.mpegurl;charset=UTF-8";
            case ".ts" -> "video/mp2t";
            case ".mp4" -> "video/mp4";
            case ".webm" -> "video/webm";
            case ".mp3" -> "audio/mpeg";
            case ".jpg", ".jpeg", ".png", ".gif", ".webp", ".avif", ".svg" -> resolveImageContentType(suffix);
            case ".json" -> "application/json;charset=UTF-8";
            case ".txt" -> "text/plain;charset=UTF-8";
            default -> "application/octet-stream";
        };
    }
}
