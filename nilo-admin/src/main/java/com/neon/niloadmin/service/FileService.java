package com.neon.niloadmin.service;

import com.neon.niloadmin.feign.storage.InnerImageFeignClient;
import com.neon.niloadmin.feign.storage.InnerVideoFileFeignClient;
import com.neon.niloadmin.mapper.VideoInfoFileUploadMapper;
import com.neon.niloadmin.repository.redis.FileRedisRepository;
import com.neon.niloadmin.util.PathResolver;
import com.neon.nilocommon.entity.constants.*;
import com.neon.nilocommon.entity.enums.ResponseCode;
import com.neon.nilocommon.entity.po.VideoInfoFileUpload;
import com.neon.nilocommon.entity.query.VideoInfoFileUploadQuery;
import com.neon.nilocommon.entity.vo.ResponseVO;
import com.neon.nilocommon.exception.BusinessException;
import com.neon.nilocommon.util.FfmpegUtil;
import com.neon.nilocommon.util.FileUtil;
import io.minio.CopyObjectArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.SourceObject;
import io.minio.errors.MinioException;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@RequiredArgsConstructor
@Service
public class FileService
{
    private static final int PRESIGNED_URL_EXPIRE_SECONDS = 3600;

    private final VideoInfoFileUploadMapper <VideoInfoFileUpload, VideoInfoFileUploadQuery> videoInfoFileUploadMapper;

    private final FileRedisRepository fileRedisRepository;

    private final PathResolver pathResolver;

    private final InnerImageFeignClient innerImageFeignClient;

    private final InnerVideoFileFeignClient innerVideoFileFeignClient;

    private final MinioClient minioClient;

    /**
     * 上传图片至 MinIO public 前缀
     *
     * @return 图片 plain key
     */
    public String uploadImage(MultipartFile file)
    {
        // 先校验下图片是否合法，顺便获取图片的contentType
        String contentType = FileUtil.validateImageFile(file);
        String suffix = FileUtil.getImageSuffixByContentType(contentType);
        if (suffix == null)
        {
            throw new BusinessException(ResponseCode.INVALID_ARGUMENTS);
        }

        // 获取日期
        String dateName = LocalDate.now().format(DateTimeFormatter.ofPattern(DatePattern.DATE));

        // 给上传的图片改名（后缀按真实格式，不信任用户文件名）
        String savedFileName = RandomStringUtils.insecure().nextAlphanumeric(30) + suffix;

        // 设置好在minio储存的key
        String plainImgKey = String.join("/", dateName, savedFileName);
        String imgKey = MinioKey.TMP_PREFIX + plainImgKey;

        // 直接上传到 MinIO
        upload(file, contentType, plainImgKey, imgKey);

        return plainImgKey;
    }

    /**
     * 获取图片预签名 URL<hr/>
     * 在 tmp/pending/public 三个前缀中探测实际位置，供审核等场景按需查看
     */
    public String downloadImage(String plainKey)
    {
        if (!FileUtil.isValidImagePlainKey(plainKey))
        {
            throw new BusinessException(ResponseCode.INVALID_ARGUMENTS);
        }

        ResponseVO <String> probeResult = innerImageFeignClient.probeImageObjectKey(plainKey);
        if (!probeResult.getCode().equals(ResponseCode.SUCCESS.getCode()))
        {
            throw new BusinessException(ResponseCode.NOT_FOUND);
        }

        String minioKey = probeResult.getData();
        return getPresignedUrl(minioKey);
    }

    /**
     * 获取主m3u8
     */
    public void downloadVideoMasterM3u8(HttpServletResponse response, Long videoId, Integer index)
    {
        String baseKey = queryVideoUploadFilePath(videoId, index);
        serveMasterM3u8(response, baseKey);
    }

    /**
     * 获取m3u8列表
     */
    public void downloadVideoPlaylistM3u8(HttpServletResponse response, Long videoId, Integer index, String folder)
    {
        String baseKey = queryVideoUploadFilePath(videoId, index);
        servePlaylistM3u8(response, baseKey, folder);
    }

    /**
     * 从 MinIO 拉取 master.m3u8 并写入响应<hr/>
     * master.m3u8 中的相对路径 {720P|480P}/index.m3u8 会自然解析到服务端端点，无需重写
     *
     * @param response HttpServletResponse
     * @param baseKey  视频文件的 baseKey（不含前缀）
     */
    public void serveMasterM3u8(HttpServletResponse response, String baseKey)
    {
        String minioKey = MinioKey.PENDING_PREFIX + baseKey + "/" + Constants.MASTER_M3U8_NAME;
        if (!FileUtil.isValidVideoHlsObjectKey(minioKey))
        {
            throw new BusinessException(ResponseCode.INVALID_ARGUMENTS);
        }
        String content = fetchTextFromMinio(minioKey);
        FileUtil.writeM3u8Response(response, content);
    }

    /**
     * 从 MinIO 拉取分辨率 playlist（index.m3u8），将 TS 相对路径重写为 MinIO presigned URL，写入响应<hr/>
     * 重写后前端将直接从 MinIO 下载 TS 分片，不再经过服务端
     *
     * @param response   HttpServletResponse
     * @param baseKey    视频文件的 baseKey（不含前缀）
     * @param folderName 清晰度目录名（720P / 480P）
     */
    public void servePlaylistM3u8(HttpServletResponse response, String baseKey, String folderName)
    {
        String folder = FileUtil.resolveResolutionFolder(folderName);
        String minioKey = MinioKey.PENDING_PREFIX + baseKey + "/" + folder + "/" + Constants.M3U8_NAME;
        if (!FileUtil.isValidVideoHlsObjectKey(minioKey))
        {
            throw new BusinessException(ResponseCode.INVALID_ARGUMENTS);
        }

        String content = fetchTextFromMinio(minioKey);
        String rewritten = rewriteTsPathsToPresignedUrls(content, baseKey, folder);

        FileUtil.writeM3u8Response(response, rewritten);
    }

    /**
     * 获取预签名URL
     *
     * @param minioKey 完整的minio key
     * @return 预签名URL
     */
    private String getPresignedUrl(String minioKey)
    {
        // 从缓存中获取预签名URL
        String cached = fileRedisRepository.getPresignedUrlCache(minioKey);
        if (cached != null)
        {
            return cached;
        }

        ResponseVO <String> result;
        if (minioKey.contains(".m3u8") || minioKey.endsWith(".ts"))
        {
            result = innerVideoFileFeignClient.downloadVideo(minioKey, PRESIGNED_URL_EXPIRE_SECONDS);
        }
        else
        {
            result = innerImageFeignClient.downloadImage(minioKey, PRESIGNED_URL_EXPIRE_SECONDS);
        }

        if (!result.getCode().equals(ResponseCode.SUCCESS.getCode()))
        {
            throw new BusinessException(ResponseCode.NOT_FOUND);
        }

        String presignedUrl = result.getData();

        // 将结果缓存
        fileRedisRepository.setPresignedUrlCache(minioKey, presignedUrl, PRESIGNED_URL_EXPIRE_SECONDS - 60);

        return presignedUrl;
    }

    /**
     * 从 MinIO 通过 presigned URL 拉取文本文件内容
     */
    private String fetchTextFromMinio(String minioKey)
    {
        String presignedUrl = getPresignedUrl(minioKey);
        try (InputStream is = URI.create(presignedUrl).toURL().openStream())
        {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
        catch (IOException e)
        {
            throw new RuntimeException("从MinIO拉取文件失败: " + minioKey, e);
        }
    }

    /**
     * 将 playlist m3u8 中的 TS 相对路径重写为 MinIO presigned URL
     */
    private String rewriteTsPathsToPresignedUrls(String m3u8Content, String baseKey, String folder)
    {
        Pattern tsPattern = Pattern.compile("^" + Pattern.quote(Constants.TS_FOLDER_NAME) + "/(\\d{4}\\.ts)$", Pattern.MULTILINE);
        Matcher matcher = tsPattern.matcher(m3u8Content);

        StringBuilder output = new StringBuilder();

        while (matcher.find())
        {
            String segName = matcher.group(1);
            String tsMinioKey = MinioKey.PENDING_PREFIX + baseKey + "/" + folder + "/" + Constants.TS_FOLDER_NAME + "/" + segName;
            String presignedUrl = getPresignedUrl(tsMinioKey);
            matcher.appendReplacement(output, Matcher.quoteReplacement(presignedUrl));
        }
        matcher.appendTail(output);

        return output.toString();
    }

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
     * 上传图片至MinIO
     *
     * @param file        图片文件
     * @param contentType 文件类型
     * @param baseKey
     * @param key         文件在MinIO中的key
     */
    private void upload(MultipartFile file, String contentType, String baseKey, String key)
    {
        // 小图：原图即缩略图，不落本地、不跑 ffmpeg
        if (file.getSize() <= Threshold.IMAGE_ZIP_THRESHOLD)
        {
            try
            {
                try (InputStream imageStream = file.getInputStream())
                {
                    minioClient.putObject(PutObjectArgs.builder()
                                                       .bucket(MinioBucket.MINIO_IMAGE_BUCKET)
                                                       .object(key)
                                                       .stream(imageStream, file.getSize(), -1L)
                                                       .contentType(contentType)
                                                       .build());
                }

                // 缩略图与原图内容相同，用 copy 避免二次读流 / 落盘
                minioClient.copyObject(CopyObjectArgs.builder()
                                                     .bucket(MinioBucket.MINIO_IMAGE_BUCKET)
                                                     .object(FileUtil.constructThumbnailName(key))
                                                     .source(SourceObject.builder()
                                                                         .bucket(MinioBucket.MINIO_IMAGE_BUCKET)
                                                                         .object(key)
                                                                         .build())
                                                     .build());
            }
            catch (IOException | MinioException e)
            {
                throw new RuntimeException(e);
            }
        }
        // 大图：落本地后用 ffmpeg 生成缩略图再上传
        else
        {
            // 相对路径经 PathResolver 落到应用目录
            Path relativePath = Path.of(Constants.FILE_FOLDER_NAME, Constants.TMP_FOLDER_NAME, baseKey);
            Path absDest = pathResolver.resolve(relativePath.toString());
            String thumbnailPathStr = null;

            try (InputStream imageStream = file.getInputStream())
            {
                // 必须对绝对路径建目录；对相对路径 createDirectories 建的不是 absDest 的父目录
                Files.createDirectories(absDest.getParent());
                Files.copy(imageStream, absDest, StandardCopyOption.REPLACE_EXISTING);

                // 生成缩略图（ffmpeg 需要真实可读路径）
                thumbnailPathStr = FfmpegUtil.creatImgThumbnail(absDest.toString(), false);
                Path thumbnailPath = Path.of(thumbnailPathStr);

                // 上传原图和缩略图到MinIO中
                try (InputStream localImageStream = Files.newInputStream(absDest) ;
                     InputStream thumbnailStream = Files.newInputStream(thumbnailPath))
                {
                    minioClient.putObject(PutObjectArgs.builder()
                                                       .bucket(MinioBucket.MINIO_IMAGE_BUCKET)
                                                       .object(key)
                                                       .stream(localImageStream, Files.size(absDest), -1L)
                                                       .contentType(contentType)
                                                       .build());

                    minioClient.putObject(PutObjectArgs.builder()
                                                       .bucket(MinioBucket.MINIO_IMAGE_BUCKET)
                                                       .object(FileUtil.constructThumbnailName(key))
                                                       .stream(thumbnailStream, Files.size(thumbnailPath), -1L)
                                                       .contentType(contentType)
                                                       .build());
                }
            }
            catch (IOException | MinioException e)
            {
                throw new RuntimeException(e);
            }
            finally
            {
                try
                {
                    Files.deleteIfExists(absDest);
                    if (thumbnailPathStr != null)
                    {
                        Files.deleteIfExists(Path.of(thumbnailPathStr));
                    }
                }
                catch (IOException e)
                {
                    log.warn("删除文件失败，文件路径：{}，异常信息：{}",
                             List.of(absDest.toString(), thumbnailPathStr),
                             e.toString());
                }
            }
        }
    }

}
