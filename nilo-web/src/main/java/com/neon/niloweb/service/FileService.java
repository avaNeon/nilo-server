package com.neon.niloweb.service;


import com.neon.nilocommon.config.SystemConfig;
import com.neon.nilocommon.entity.constants.*;
import com.neon.nilocommon.entity.enums.ResponseCode;
import com.neon.nilocommon.entity.po.MediaOwnership;
import com.neon.nilocommon.entity.po.VideoInfoFileUpload;
import com.neon.nilocommon.entity.po.redis.TokenUserInfo;
import com.neon.nilocommon.entity.query.MediaOwnershipQuery;
import com.neon.nilocommon.entity.query.VideoInfoFileUploadQuery;
import com.neon.nilocommon.entity.vo.ResponseVO;
import com.neon.nilocommon.exception.BusinessException;
import com.neon.nilocommon.repository.redis.SystemConfigRedisRepository;
import com.neon.nilocommon.util.FfmpegUtil;
import com.neon.nilocommon.util.FileUtil;
import com.neon.nilocommon.util.TimeUtil;
import com.neon.niloweb.enums.UploadQuotaType;
import com.neon.niloweb.feign.storage.ImageFeignClient;
import com.neon.niloweb.feign.storage.VideoFileFeignClient;
import com.neon.niloweb.mapper.MediaOwnershipMapper;
import com.neon.niloweb.mapper.VideoInfoFileUploadMapper;
import com.neon.niloweb.repository.redis.FileRedisRepository;
import com.neon.niloweb.util.PathResolver;
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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@RequiredArgsConstructor
@Service
public class FileService
{
    /**
     * 预签名URL过期时间（秒），与Redis缓存TTL保持一致
     */
    private static final int PRESIGNED_URL_EXPIRE_SECONDS = 3600;

    private final SystemConfig systemConfig;

    private final PathResolver pathResolver;

    private final UploadQuotaService uploadQuotaService;

    private final UploadService uploadService;

    private final MediaOwnershipMapper <MediaOwnership, MediaOwnershipQuery> mediaOwnershipMapper;

    private final VideoInfoFileUploadMapper <VideoInfoFileUpload, VideoInfoFileUploadQuery> videoInfoFileUploadMapper;

    private final SystemConfigRedisRepository systemConfigRedisRepository;

    private final FileRedisRepository fileRedisRepository;

    private final ImageFeignClient imageFeignClient;

    private final VideoFileFeignClient videoFileFeignClient;

    private final MinioClient minioClient;

    // 图片和视频保存路径（相对路径）：file/tmp/<datetime>/<name>

    /**
     * 上传图片<hr/>
     *
     * @param userId 用户ID
     * @param file   图片文件
     * @return 文件在minIO中的key（不含tmp前缀，不包含缩略图）
     */
    @Transactional(rollbackFor = Exception.class)
    public String uploadImage(long userId, MultipartFile file)
    {
        // --- 校验 ---

        // 不能超过大小限制
        if (file.getSize() > (long) systemConfigRedisRepository.getSystemConfig().getImageMaxSize() * Constants.Mebibyte)
        {
            throw new BusinessException("文件大小超过限制");
        }

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
        String plainThumbnailKey = String.join("/", dateName, FileUtil.constructThumbnailName(savedFileName));
        String imgKey = MinioKey.TMP_PREFIX + plainImgKey;

        // 构建图片所有权信息
        MediaOwnership imgOwnership = new MediaOwnership();
        imgOwnership.setOwnerId(userId);
        imgOwnership.setObjectKey(plainImgKey);
        imgOwnership.setBucket(MinioBucket.MINIO_IMAGE_BUCKET);
        imgOwnership.setCreatedTime(LocalDateTime.now());
        imgOwnership.setUsed(0);
        MediaOwnership thumbnailOwnership = new MediaOwnership();
        thumbnailOwnership.setOwnerId(userId);
        thumbnailOwnership.setObjectKey(plainThumbnailKey);
        thumbnailOwnership.setBucket(MinioBucket.MINIO_IMAGE_BUCKET);
        thumbnailOwnership.setCreatedTime(LocalDateTime.now());
        thumbnailOwnership.setUsed(0);

        // 将图片所有权信息批量插入数据库
        mediaOwnershipMapper.insertBatch(List.of(imgOwnership, thumbnailOwnership));

        // 直接上传到 MinIO
        upload(file, contentType, plainImgKey, imgKey);

        // 将key返回给调用者，注意：返回非TMP的key
        return plainImgKey;
    }

    /**
     * 获取图片预签名URL<hr/>
     * 仅用于pending状态的图片，属主访问自己的待审核资源
     *
     * @param userId 用户ID
     * @param imgKey 图片key（不含前缀）
     * @return 预签名URL
     */
    public String downloadImage(long userId, String imgKey)
    {
        // 校验归属权（必须是属主且used=1）
        MediaOwnership ownership = mediaOwnershipMapper.selectByObjectKeyAndOwnerIdAndUsed(imgKey, userId, 1);
        if (ownership == null)
        {
            throw new BusinessException(ResponseCode.NOT_FOUND);
        }

        // 查Redis缓存
        String cached = fileRedisRepository.getPresignedUrlCache(imgKey);
        if (cached != null)
        {
            return cached;
        }

        // 向nilo-storage请求预签名URL（pending前缀）
        String minioKey = MinioKey.PENDING_PREFIX + imgKey;
        ResponseVO <String> result = imageFeignClient.downloadImage(minioKey, PRESIGNED_URL_EXPIRE_SECONDS);
        if (!result.getCode().equals(ResponseCode.SUCCESS.getCode()))
        {
            throw new BusinessException("获取图片下载地址失败");
        }

        String presignedUrl = result.getData();

        // 缓存到Redis，TTL比预签名URL短一点
        fileRedisRepository.setPresignedUrlCache(imgKey, presignedUrl, PRESIGNED_URL_EXPIRE_SECONDS - 60);

        return presignedUrl;
    }

    /**
     * 获取上传视频文件的预签名form
     *
     * @param userId   用户id
     * @param fileSize 用户声明的文件大小（单位：字节）
     */
    public Map <String, String> uploadVideo(long userId, long fileSize)
    {
        // 预先记录时间
        LocalDateTime now = LocalDateTime.now();

        // 接下来进入临界区
        // 由于这个方法需要加锁，并且需要单独加上事务，所以放到衍生类中调用，防止调用本类下的方法没有Spring代理
        String key = uploadService.getUploadKey(now, userId);

        // 接下来生成 presigned post form 并传给用户

        // 给用户的限额应该是 min(用户当天剩余用量、每个视频文件最大大小、用户声明的大小、这样保证不超用量)

        long remainingQuota = uploadQuotaService.getRemainingQuota(userId, UploadQuotaType.VIDEO, now.toLocalDate());

        long maxVideoSize = (long) systemConfig.getVideoFileMaxSize() * Constants.Mebibyte;

        // 限定好时间，只能在今日使用，因为created_time里的时间就是今日
        long secondsUntilTomorrow = TimeUtil.getSecondsUntilTomorrow(now);

        // 如果现在距离明天还有30s，就不让用户上传文件了，容易导致文件限额记错日期
        if (secondsUntilTomorrow < 30)
        {
            throw new BusinessException("服务器忙，请稍后再试");
        }

        // 减少30s，防止用户拖到一天的最后30s才提交，彻底消灭卡时间的可能
        secondsUntilTomorrow -= 30;

        // 申请一个 presigned post form
        // MinIO getPresignedPostFormData 只返回签名字段，不含 key / Content-Type，需由调用方补上
        String objectKey = MinioKey.TMP_PREFIX + key;
        ResponseVO <Map <String, String>> responseVO = videoFileFeignClient.upload(objectKey,
                                                                                   secondsUntilTomorrow,
                                                                                   Math.min(fileSize,
                                                                                            Math.min(maxVideoSize,
                                                                                                     remainingQuota)));

        if (!responseVO.getCode().equals(ResponseCode.SUCCESS.getCode()))
        {
            throw new BusinessException("网络异常");
        }

        Map <String, String> formData = responseVO.getData();
        if (formData == null)
        {
            throw new BusinessException("网络异常");
        }
        // 前端直传 MinIO 时必须带上 key（与 PostPolicy 中的 eq 条件一致）
        formData.put("key", objectKey);
        return formData;
    }

    /**
     * 获取主M3U8
     *
     * @param userId  当前登录用户ID
     * @param videoId 视频ID
     * @param index   文件序号
     */
    public void downloadVideoMasterM3u8(long userId, Long videoId, Integer index, HttpServletResponse response)
    {
        VideoInfoFileUpload uploadFile = selectOwnedVideoInfoFileUpload(userId, videoId, index);
        serveMasterM3u8(response, uploadFile.getFilePath());
    }

    /**
     * 获取指定分辨率的M3U8
     *
     * @param userId  当前登录用户ID
     * @param videoId 视频ID
     * @param index   文件序号
     * @param folder  清晰度目录名（720P / 480P）
     */
    public void downloadVideoPlaylistM3u8(long userId, Long videoId, Integer index, String folder, HttpServletResponse response)
    {
        VideoInfoFileUpload uploadFile = selectOwnedVideoInfoFileUpload(userId, videoId, index);
        servePlaylistM3u8(response, uploadFile.getFilePath(), folder);
    }

    /**
     * 清理过期的文件所有权信息<hr/>
     * <ul>
     *     <li>清理超时expireHour且未被使用的文件所有权信息</li>
     *     <li>由于{@link CreativeCenterService#videoUpload(Long, String, String, Integer, Integer, Short, String, String, String, String, List, List, List, TokenUserInfo)}方法可能会导致部分文件上传失败，部分文件成功的情况。<br/>
     *     所以未启用的文件可能是pending状态，此时清理时必须注意同时清除这种文件</li>
     * </ul>
     *
     * @param expireHour 过期小时数
     */
    @Transactional(rollbackFor = Exception.class)
    public void clearExpiredOwnership(int expireHour)
    {
        LocalDateTime now = LocalDateTime.now();

        // 先查一下过期记录，用X锁锁定
        List <MediaOwnership> expiredRecords = mediaOwnershipMapper.selectExpiredForUpdate(now, expireHour);
        // 没有过期记录就直接返回
        if (expiredRecords == null || expiredRecords.isEmpty())
        {
            return;
        }

        // 删除这些记录
        mediaOwnershipMapper.deleteExpiredRecord(now, expireHour);

        // 将过期记录按bucket分类，方便之后调用nilo-storage的批量删除接口
        List <String> imageKeys = new ArrayList <>();
        List <String> videoKeys = new ArrayList <>();
        for (MediaOwnership record : expiredRecords)
        {
            if (MinioBucket.MINIO_IMAGE_BUCKET.equals(record.getBucket()))
            {
                imageKeys.add(record.getObjectKey());
                // 以防万一，把略缩图也加上
                imageKeys.add(FileUtil.constructThumbnailName(record.getObjectKey()));
            }
            else if (MinioBucket.MINIO_VIDEO_BUCKET.equals(record.getBucket()))
            {
                videoKeys.add(record.getObjectKey());
            }
        }

        // --- 删除文件 ---

        if (!imageKeys.isEmpty())
        {
            ResponseVO <Void> responseVO = imageFeignClient.batchDelete(imageKeys);
            if (!responseVO.getCode().equals(ResponseCode.SUCCESS.getCode()))
            {
                log.error("删除图片失败");
            }
        }

        if (!videoKeys.isEmpty())
        {
            ResponseVO <Void> responseVO = videoFileFeignClient.batchDeleteRecursively(videoKeys);
            if (!responseVO.getCode().equals(ResponseCode.SUCCESS.getCode()))
            {
                log.error("删除视频失败");
            }
        }
    }

    /**
     * 查询当前用户拥有的唯一视频上传文件记录
     */
    private VideoInfoFileUpload selectOwnedVideoInfoFileUpload(long userId, Long videoId, Integer index)
    {
        VideoInfoFileUpload uploadFile = videoInfoFileUploadMapper.selectByVideoIdAndFileIndex(videoId, index);

        // 校验
        if (uploadFile == null)
        {
            throw new BusinessException(ResponseCode.NOT_FOUND);
        }
        if (uploadFile.getFilePath() == null || uploadFile.getFilePath().isBlank())
        {
            throw new BusinessException(ResponseCode.NOT_FOUND);
        }
        if (!Objects.equals(uploadFile.getUserId(), userId))
        {
            throw new BusinessException(ResponseCode.NOT_FOUND);
        }

        return uploadFile;
    }

    /**
     * 从 MinIO 拉取 master.m3u8 并写入响应<hr/>
     * master.m3u8 中的相对路径 {720P|480P}/index.m3u8 会自然解析到服务端端点，无需重写
     */
    private void serveMasterM3u8(HttpServletResponse response, String baseKey)
    {
        // 先校验key
        String minioKey = MinioKey.PENDING_PREFIX + baseKey + "/" + Constants.MASTER_M3U8_NAME;
        if (!FileUtil.isValidVideoHlsObjectKey(minioKey))
        {
            throw new BusinessException(ResponseCode.INVALID_ARGUMENTS);
        }

        // 获取m3u8
        String content = fetchTextFromMinio(minioKey);

        // 写入响应
        FileUtil.writeM3u8Response(response, content);
    }

    /**
     * 从 MinIO 拉取分辨率 playlist（index.m3u8），将 TS 相对路径重写为 MinIO presigned URL，写入响应<hr/>
     * 重写后前端将直接从 MinIO 下载 TS 分片，不再经过服务端
     */
    private void servePlaylistM3u8(HttpServletResponse response, String baseKey, String folderName)
    {
        // 先校验key
        String folder = FileUtil.resolveResolutionFolder(folderName);
        String minioKey = MinioKey.PENDING_PREFIX + baseKey + "/" + folder + "/" + Constants.M3U8_NAME;
        if (!FileUtil.isValidVideoHlsObjectKey(minioKey))
        {
            throw new BusinessException(ResponseCode.INVALID_ARGUMENTS);
        }

        // 获取m3u8并重写
        String content = fetchTextFromMinio(minioKey);
        String rewritten = rewriteTsPathsToPresignedUrls(content, baseKey, folder);

        // 写入响应
        FileUtil.writeM3u8Response(response, rewritten);
    }

    /**
     * 获取预签名URL
     *
     * @param minioKey 完整的minio key（含前缀）
     * @return 预签名URL
     */
    private String getPresignedUrl(String minioKey)
    {
        String cached = fileRedisRepository.getPresignedUrlCache(minioKey);
        if (cached != null)
        {
            return cached;
        }

        ResponseVO <String> result;
        if (minioKey.contains(".m3u8") || minioKey.endsWith(".ts"))
        {
            result = videoFileFeignClient.downloadVideo(minioKey, PRESIGNED_URL_EXPIRE_SECONDS);
        }
        else
        {
            result = imageFeignClient.downloadImage(minioKey, PRESIGNED_URL_EXPIRE_SECONDS);
        }

        if (!result.getCode().equals(ResponseCode.SUCCESS.getCode()))
        {
            throw new BusinessException(ResponseCode.NOT_FOUND);
        }

        String presignedUrl = result.getData();
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
        // 写个正则表达式用于匹配
        Pattern tsPattern = Pattern.compile("^" + Pattern.quote(Constants.TS_FOLDER_NAME) + "/(\\d{4}\\.ts)$", Pattern.MULTILINE);
        Matcher matcher = tsPattern.matcher(m3u8Content);

        StringBuilder output = new StringBuilder();

        // 开始匹配并替换
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
            // 相对路径经 PathResolver 落到应用目录（IDE 下多为 target/classes）
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
