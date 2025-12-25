package com.neon.niloweb.service;


import cn.hutool.core.lang.Snowflake;
import com.neon.nilocommon.entity.constants.Constants;
import com.neon.nilocommon.entity.constants.DatePattern;
import com.neon.nilocommon.entity.constants.RedisKey;
import com.neon.nilocommon.entity.dto.TokenUserInfo;
import com.neon.nilocommon.entity.dto.UploadingVideoFile;
import com.neon.nilocommon.entity.enums.ResponseCode;
import com.neon.nilocommon.exception.BusinessException;
import com.neon.nilocommon.util.StringUtil;
import com.neon.niloweb.config.SystemConfig;
import com.neon.niloweb.config.WebConfig;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;


@RequiredArgsConstructor
@Service
public class FileService
{
    private final WebConfig webConfig;
    private final SystemConfig systemConfig;
    private final RedisTemplate <String, Object> redisTemplate;
    private final Snowflake snowflake;

    /**
     * 下载图片
     *
     * @param response HttpServletResponse
     * @param fileName 文件名
     */
    public void downloadImage(HttpServletResponse response, String fileName)
    {
        if (!StringUtil.isValidPath(fileName)) throw new BusinessException("非法的文件路径");
        String suffix = StringUtil.getSuffix(fileName);
        response.setContentType("image/" + suffix.replace(".", ""));
        response.setHeader("Cache-Control", "max-age=2592000"); // 一年
        readFile(response, fileName);
    }

    /**
     * 预上传视频
     *
     * @param fileName      文件名
     * @param chunkSize     （视频文件）分块大小
     * @param file          文件
     * @param tokenUserInfo 用户信息DTO（带token）
     */
    public Long preUploadVideo(String fileName, Integer chunkSize, TokenUserInfo tokenUserInfo)
    {
        UploadingVideoFile video = new UploadingVideoFile();
        Long uploadId = snowflake.nextId();
        video.setUploadId(uploadId);
        video.setFileName(fileName);
        video.setChunkSize(chunkSize);
        video.setChunkIndex(0);
        String day = LocalDate.now().format(DateTimeFormatter.ofPattern(DatePattern.DATE)); // 使用Java8+的时间类生成指定格式的时间字符串
        String filePath = day + "/" + tokenUserInfo.getUserId() + "/" + uploadId;
        /*
         * 最终的文件层次是这样的：
         * file
         *   -tmp
         *     -<日期>
         *       -<用户id-1>
         *         -<uploadId-1.1>
         *         -<uploadId-1.2>
         *       -<用户id-2>
         *         -<uploadId-2.1>
         *         -<uploadId-2.2>
         */
        String absolutePath = webConfig.getRootFilePath() + "/" + Constants.FILE_FOLDER_NAME + "/" + Constants.TMP_FOLDER_NAME + "/" + filePath;
        File videoFile = new File(absolutePath);
        if (!videoFile.exists())
        {
            videoFile.mkdirs();
        }
        video.setFilePath(absolutePath);
        // 使用指定 KEY名+用户id 作为Redis键名，有效时长1天
        redisTemplate.opsForValue()
                     .set(RedisKey.UPLOADING_VIDEO_PREFIX + tokenUserInfo.getUserId() + ":" + uploadId, video, Duration.ofDays(1L));
        return uploadId;
    }

    /**
     * 上传视频
     *
     * @param chunkFile  单个分块视频文件
     * @param chunkIndex 分块索引
     * @param userId     用户id
     * @param uploadId   上传id
     */
    public void uploadVideo(MultipartFile chunkFile, Integer chunkIndex, Long userId, String uploadId)
    {
        UploadingVideoFile videoFile = (UploadingVideoFile) redisTemplate.opsForValue()
                                                                         .get(RedisKey.UPLOADING_VIDEO_PREFIX + userId + ":" + uploadId);

        if (videoFile == null) throw new BusinessException("文件不存在，请重新上传");
        // 查看视频文件是否超过限制
        if (videoFile.getFileSize() > systemConfig.getVideoSize() * Constants.Mebibyte)
        {
            throw new BusinessException("文件大小超过限制");
        }
        // 判断块号是否正确
        if ((chunkIndex - 1) > videoFile.getChunkIndex() || chunkIndex > videoFile.getChunkSize())
        {
            throw new BusinessException(ResponseCode.INVALID_ARGUMENTS);
        }

        File targetFile = new File(videoFile.getFilePath() + "/" + chunkIndex);
        try
        {
            chunkFile.transferTo(targetFile);
            videoFile.setChunkIndex(chunkIndex);
            videoFile.addFileSize(chunkFile.getSize());
            // 更新Redis种存储的视频信息
            redisTemplate.opsForValue().set(RedisKey.UPLOADING_VIDEO_PREFIX + userId + uploadId, videoFile, Duration.ofDays(1L));
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
    }

    /**
     * 从系统中读取文件，写出到response中
     */
    private void readFile(HttpServletResponse response, String fileName)
    {
        File file = new File(webConfig.getRootFilePath() + "/" + Constants.FILE_FOLDER_NAME + "/" + fileName);
        if (!file.exists()) return;
        try (ServletOutputStream outputStream = response.getOutputStream() ; FileInputStream inputStream = new FileInputStream(file))
        {
            byte[] bytes = new byte[1024];
            int len = 0;
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
