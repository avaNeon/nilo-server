package com.neon.niloweb.service;


import cn.hutool.core.lang.Snowflake;
import com.neon.nilocommon.entity.constants.Constants;
import com.neon.nilocommon.entity.constants.DatePattern;
import com.neon.nilocommon.entity.constants.RedisKey;
import com.neon.nilocommon.entity.dto.TokenUserInfo;
import com.neon.nilocommon.entity.dto.UploadedVideoFileDTO;
import com.neon.nilocommon.entity.enums.ResponseCode;
import com.neon.nilocommon.exception.BusinessException;
import com.neon.nilocommon.util.FFmpegUtil;
import com.neon.nilocommon.util.FileUtil;
import com.neon.nilocommon.util.StringUtil;
import com.neon.niloweb.config.SystemConfig;
import com.neon.niloweb.config.WebConfig;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.RandomStringUtils;
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
        if (!StringUtil.isValidPath(filePath)) throw new BusinessException("非法的文件路径");
        String suffix = StringUtil.getSuffix(filePath);
        if (suffix.equals(".jpg")) suffix = ".jpeg";
        response.setContentType("image/" + suffix.replace(".", ""));
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
        String date = LocalDate.now().format(DateTimeFormatter.ofPattern(DatePattern.DATE)); // 使用Java8+的时间类生成指定格式的时间字符串
        String filePath = date + "/" + tokenUserInfo.getUserId() + "/" + uploadId;
        /*
         * 最终的文件层次是这样的：
         * file
         *   -tmp
         *     -<date日期>
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
        video.setFilePath(filePath);
        // 使用指定 KEY名+用户id 作为Redis键名，有效时长1天
        redisTemplate.opsForValue()
                     .set(RedisKey.PRE_UPLOADED_VIDEO_TAG_PREFIX + tokenUserInfo.getUserId() + ":" + uploadId, video, Duration.ofDays(1L));
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
    public void uploadVideo(MultipartFile chunkFile, Integer chunkIndex, Long userId, String uploadId)
    {
        UploadedVideoFileDTO videoFileDTO = (UploadedVideoFileDTO) redisTemplate.opsForValue()
                                                                                .get(RedisKey.PRE_UPLOADED_VIDEO_TAG_PREFIX + userId + ":" + uploadId);

        if (videoFileDTO == null) throw new BusinessException("文件不存在，请重新上传");
        // 查看视频文件是否超过限制
        if (videoFileDTO.getFileSize() > systemConfig.getVideoMaxSize() * Constants.Mebibyte)
        {
            throw new BusinessException("文件大小超过限制");
        }
        // 判断块号是否正确
        if (((chunkIndex - 1) > videoFileDTO.getChunkIndex() || chunkIndex > videoFileDTO.getChunkSize()))
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
            redisTemplate.opsForValue()
                         .set(RedisKey.PRE_UPLOADED_VIDEO_TAG_PREFIX + userId + ":" + uploadId, videoFileDTO, Duration.ofDays(1L));
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
    public void deleteVideo(String uploadId, Long userId)
    {
        UploadedVideoFileDTO fileDTO = (UploadedVideoFileDTO) redisTemplate.opsForValue()
                                                                           .get(RedisKey.PRE_UPLOADED_VIDEO_TAG_PREFIX + userId + ":" + uploadId);
        if (fileDTO == null)
        {
            throw new BusinessException("所要删除的文件不存在");
        }
        redisTemplate.delete(RedisKey.PRE_UPLOADED_VIDEO_TAG_PREFIX + userId + ":" + uploadId);
        FileUtil.deleteFolder(new File(webConfig.getRootFilePath() + "/" + Constants.FILE_FOLDER_NAME + "/" + Constants.TMP_FOLDER_NAME + "/" + fileDTO.getFilePath()));
    }

    /**
     * 从系统中读取文件，写出到response中
     *
     * @param filePath 相对于 根路径/file 下的文件路径
     */
    private void readFile(HttpServletResponse response, String filePath)
    {
        File file = new File(webConfig.getRootFilePath() + "/" + Constants.FILE_FOLDER_NAME + "/" + filePath);
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
