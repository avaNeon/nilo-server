package com.neon.niloadmin.service;

import com.neon.niloadmin.config.AdminConfig;
import com.neon.niloadmin.mapper.VideoInfoFileUploadMapper;
import com.neon.nilocommon.entity.constants.Constants;
import com.neon.nilocommon.entity.constants.DatePattern;
import com.neon.nilocommon.entity.constants.VideoResolution;
import com.neon.nilocommon.entity.enums.ResponseCode;
import com.neon.nilocommon.entity.po.VideoInfoFileUpload;
import com.neon.nilocommon.entity.query.VideoInfoFileUploadQuery;
import com.neon.nilocommon.exception.BusinessException;
import com.neon.nilocommon.util.FfmpegUtil;
import com.neon.nilocommon.util.StringUtil;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.RandomStringUtils;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;


@RequiredArgsConstructor
@Service
public class FileService
{
    private final AdminConfig adminConfig;

    private final VideoInfoFileUploadMapper <VideoInfoFileUpload, VideoInfoFileUploadQuery> videoInfoFileUploadMapper;

    public String uploadImage(MultipartFile file, Boolean createThumbnail)
    {
        String dateName = LocalDate.now().format(DateTimeFormatter.ofPattern(DatePattern.YYYYMM)); // 目录按月划分
        String folderPath = adminConfig.getRootFilePath() + "/" + Constants.FILE_FOLDER_NAME + "/" + Constants.COVER_FOLDER_NAME + "/" + dateName;

        File folderFile = new File(folderPath);
        if (!folderFile.exists()) folderFile.mkdirs();

        String fileName = file.getOriginalFilename();
        String suffix = StringUtil.getSuffix(fileName);
        String savedFileName = RandomStringUtils.randomAlphanumeric(30) + suffix;

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
            FfmpegUtil.creatImgThumbnail(filePath, adminConfig.isShowCommandLogs());
        }

        return "/" + dateName + "/" + savedFileName;
    }

    public void downloadImage(HttpServletResponse response, String filePath, boolean tmp)
    {
        String coverRootPath = Paths.get(adminConfig.getRootFilePath(), Constants.FILE_FOLDER_NAME, Constants.COVER_FOLDER_NAME)
                                    .toString();
        String absolutePath = Paths.get(coverRootPath, filePath).toString();
        if (!StringUtil.isValidPath(coverRootPath, absolutePath)) throw new BusinessException("非法的文件路径");
        String suffix = StringUtil.getSuffix(filePath);
        response.setContentType(resolveImageContentType(suffix));
        response.setHeader("Cache-Control", "max-age=2592000"); // 30天
        String folderName = tmp ? Constants.TMP_FOLDER_NAME : Constants.COVER_FOLDER_NAME;
        readFile(response, folderName + "/" + filePath);
    }

    public void downloadVideoMasterM3u8(Long videoId, Integer index, HttpServletResponse response)
    {
        String filePath = queryVideoUploadFilePath(videoId, index);

        readFile(response, filePath + "/" + Constants.MASTER_M3U8_NAME);
    }

    public void downloadVideoPlaylistM3u8(Long videoId, Integer index, Integer resolution, HttpServletResponse response)
    {
        String filePath = queryVideoUploadFilePath(videoId, index);
        String folder = resolveResolutionFolder(resolution);

        readFile(response, filePath + "/" + folder + "/" + Constants.M3U8_NAME);
    }

    public void downloadVideoSegmentTs(Long videoId,
                                       Integer index,
                                       Integer resolution,
                                       String segment,
                                       HttpServletResponse response)
    {
        // 校验段名是否合法
        if (!isValidSegmentName(segment))
        {
            throw new BusinessException(ResponseCode.INVALID_ARGUMENTS);
        }

        String filePath = queryVideoUploadFilePath(videoId, index);
        String folder = resolveResolutionFolder(resolution);

        readFile(response, filePath + "/" + folder + "/" + Constants.TS_FOLDER_NAME + "/" + segment);
    }

    /**
     * 获取对应解析度的文件夹名
     *
     * @param resolution 解析度
     * @return 文件夹名
     */
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

    /**
     * 查询单个视频文件路径
     *
     * @param videoId   视频ID
     * @param fileIndex 文件序号
     * @return 文件路径
     */
    private String queryVideoUploadFilePath(Long videoId, Integer fileIndex)
    {
        VideoInfoFileUploadQuery uploadQuery = new VideoInfoFileUploadQuery();
        uploadQuery.setVideoId(videoId);
        uploadQuery.setFileIndex(fileIndex);
        List <VideoInfoFileUpload> uploadFiles = videoInfoFileUploadMapper.selectList(uploadQuery);
        if (uploadFiles == null || uploadFiles.isEmpty() || uploadFiles.get(0).getFilePath() == null)
        {
            throw new BusinessException(ResponseCode.NOT_FOUND);
        }
        return uploadFiles.get(0).getFilePath();
    }

    /**
     * 从系统中读取文件，写出到response中
     */
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
}
