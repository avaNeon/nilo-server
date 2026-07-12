package com.neon.nilostorage.service;

import com.neon.nilocommon.entity.constants.Constants;
import com.neon.nilocommon.entity.constants.MinioBucket;
import com.neon.nilocommon.entity.constants.MinioKey;
import com.neon.nilocommon.entity.constants.Threshold;
import com.neon.nilocommon.entity.enums.ResponseCode;
import com.neon.nilocommon.exception.BusinessException;
import com.neon.nilocommon.util.FfmpegUtil;
import com.neon.nilocommon.util.FileUtil;
import io.minio.*;
import io.minio.errors.ErrorResponseException;
import io.minio.errors.MinioException;
import io.minio.messages.DeleteRequest;
import io.minio.messages.DeleteResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;


@Slf4j
@Service
@RequiredArgsConstructor
public class ImageService
{
    private final MinioClient minioClient;

    /**
     * 上传图片至MinIO
     *
     * @param file        图片文件
     * @param contentType 文件类型
     * @param key         文件在MinIO中的key
     */
    public void upload(MultipartFile file, String contentType, String key)
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
            // 上传内容先落到本地临时路径（key 本身含 tmp/pending/public 前缀，本地多一层 file/tmp 可接受）
            Path filePath = Path.of(Constants.FILE_FOLDER_NAME, Constants.TMP_FOLDER_NAME, key);
            String thumbnailPathStr = null;

            try
            {
                // 先创建父目录
                Files.createDirectories(filePath.getParent());
                // 将文件传输到本地路径
                file.transferTo(filePath.toFile());

                // 生成缩略图
                thumbnailPathStr = FfmpegUtil.creatImgThumbnail(filePath.toString(), false);
                Path thumbnailPath = Path.of(thumbnailPathStr);

                // 上传原图和缩略图到MinIO中
                try (InputStream imageStream = Files.newInputStream(filePath) ; InputStream thumbnailStream = Files.newInputStream(
                        thumbnailPath))
                {
                    minioClient.putObject(PutObjectArgs.builder()
                                                       .bucket(MinioBucket.MINIO_IMAGE_BUCKET)
                                                       .object(key)
                                                       .stream(imageStream, Files.size(filePath), -1L)
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
                    // 删除本地临时文件
                    Files.deleteIfExists(filePath);
                    if (thumbnailPathStr != null)
                    {
                        Files.deleteIfExists(Path.of(thumbnailPathStr));
                    }
                }
                catch (IOException e)
                {
                    log.warn("删除文件失败，文件路径：{}，异常信息：{}",
                             List.of(filePath.toString(), thumbnailPathStr),
                             e.toString());
                }
            }
        }
    }

    /**
     * 修改图片的key<hr/>
     * <p>此操作幂等、可重入，不保证原子性</p>
     * <p>移动前会先探测目标key是否已存在：如果已存在，说明之前的移动已经生效（可能是重试请求），直接跳过，不再报错</p>
     * <p>只有当源key和目标key都不存在时，才认为是真正的异常状态并抛出异常</p>
     *
     * @param srcKey  图片的原key
     * @param destKey 图片的新key
     */
    public void move(String srcKey, String destKey)
    {
        // 目标key已存在，说明该文件之前已经移动成功，直接跳过（幂等）
        if (objectExists(destKey))
        {
            return;
        }

        // 目标key不存在，但源key也不存在，说明文件既没有移动过也找不到，属于真正的异常状态
        if (!objectExists(srcKey))
        {
            throw new BusinessException(ResponseCode.NOT_FOUND);
        }

        try
        {
            // 先复制
            minioClient.copyObject(CopyObjectArgs.builder()
                                                 .bucket(MinioBucket.MINIO_IMAGE_BUCKET)
                                                 .object(destKey)
                                                 .source(SourceObject.builder()
                                                                     .bucket(MinioBucket.MINIO_IMAGE_BUCKET)
                                                                     .object(srcKey)
                                                                     .build())
                                                 .build());

            // 再删除原始key
            minioClient.removeObject(RemoveObjectArgs.builder().bucket(MinioBucket.MINIO_IMAGE_BUCKET).object(srcKey).build());
        }
        catch (MinioException e)
        {
            throw new RuntimeException("移动图片失败: " + srcKey + " -> " + destKey, e);
        }
    }

    /**
     * 批量移动图片的key<hr/>
     * 此操作幂等、可重入，不保证原子性
     *
     * @param keyMap key映射表，key为原始key，value为目标key
     */
    public void batchMove(Map <String, String> keyMap)
    {
        keyMap.forEach(this::move);
    }

    /**
     * 删除图片<hr/>
     * <p>此操作具有幂等性，经过<b>实测</b>删除不存在的文件不会报错</p>
     * <p>不管状态如何都会删除</p>
     *
     * @param baseKey 文件在MinIO中的<b>不带前缀</b>的key
     */
    public void delete(String baseKey)
    {
        try
        {
            List <DeleteRequest.Object> objects = new ArrayList <>();
            objects.add(new DeleteRequest.Object(MinioKey.TMP_PREFIX + baseKey));
            objects.add(new DeleteRequest.Object(MinioKey.PENDING_PREFIX + baseKey));
            objects.add(new DeleteRequest.Object(MinioKey.PUBLIC_PREFIX + baseKey));
            deleteObjects(objects);
        }
        catch (Exception e)
        {
            throw new RuntimeException("删除图片失败: " + baseKey, e);
        }
    }

    /**
     * 批量删除图片<hr/>
     * 收集所有baseKey在三个前缀下的对象，一次性批量删除
     *
     * @param baseKeys 不带前缀的minio key列表
     */
    public void batchDelete(List <String> baseKeys)
    {
        if (baseKeys == null || baseKeys.isEmpty())
        {
            return;
        }

        List <DeleteRequest.Object> objects = new ArrayList <>();
        for (String baseKey : baseKeys)
        {
            objects.add(new DeleteRequest.Object(MinioKey.TMP_PREFIX + baseKey));
            objects.add(new DeleteRequest.Object(MinioKey.PENDING_PREFIX + baseKey));
            objects.add(new DeleteRequest.Object(MinioKey.PUBLIC_PREFIX + baseKey));
        }
        try
        {
            deleteObjects(objects);
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }

    /**
     * 执行批量删除，遍历结果以触发实际删除并检查错误
     *
     * @param objects 待删除对象列表
     */
    private void deleteObjects(List <DeleteRequest.Object> objects) throws Exception
    {
        if (objects == null || objects.isEmpty())
        {
            return;
        }

        Iterable <Result <DeleteResult.Error>> errors = minioClient.removeObjects(RemoveObjectsArgs.builder()
                                                                                                   .bucket(MinioBucket.MINIO_IMAGE_BUCKET)
                                                                                                   .objects(objects)
                                                                                                   .build());
        boolean hasFailed = false;

        for (Result <DeleteResult.Error> errorResult : errors)
        {
            hasFailed = true;
            DeleteResult.Error error = errorResult.get();
            log.warn("批量删除失败: objectName={}, message={}", error.objectName(), error.message());
        }

        if (hasFailed)
        {
            throw new RuntimeException("文件删除失败");
        }
    }

    /**
     * 获取图片在MinIO中的文件大小
     *
     * @param key 文件在MinIO中的key
     * @return 文件大小（字节）
     */
    public long getImageSize(String key)
    {
        try
        {
            StatObjectResponse stat = minioClient.statObject(StatObjectArgs.builder()
                                                                           .bucket(MinioBucket.MINIO_IMAGE_BUCKET)
                                                                           .object(key)
                                                                           .build());
            return stat.size();
        }
        catch (MinioException e)
        {
            throw new RuntimeException(e);
        }
    }

    /**
     * 探测图片在三个前缀（tmp/、pending/、public/）下的完整 object key
     *
     * @param plainKey 不带前缀的图片 key
     * @return 带前缀的 MinIO object key
     */
    public String probeImageObjectKey(String plainKey)
    {
        if (!FileUtil.isValidImagePlainKey(plainKey))
        {
            throw new BusinessException(ResponseCode.INVALID_ARGUMENTS);
        }

        String[] keys = {MinioKey.TMP_PREFIX + plainKey, MinioKey.PENDING_PREFIX + plainKey, MinioKey.PUBLIC_PREFIX + plainKey};

        String foundKey = null;
        int foundCount = 0;

        for (String key : keys)
        {
            if (objectExists(key))
            {
                foundCount++;
                foundKey = key;
            }
        }

        if (foundCount == 0)
        {
            throw new BusinessException(ResponseCode.NOT_FOUND);
        }
        if (foundCount > 1)
        {
            throw new BusinessException(ResponseCode.UNKNOWN_ERROR);
        }

        return foundKey;
    }

    /**
     * 请求用于下载的 presigned url
     *
     * @param key           文件在MinIO中的key
     * @param expireSeconds 过期时间（秒）
     * @return 图片的预签名URL
     */
    public String downloadImage(String key, int expireSeconds)
    {
        if (!FileUtil.isValidPrefixedImageObjectKey(key))
        {
            throw new BusinessException(ResponseCode.INVALID_ARGUMENTS);
        }

        try
        {
            // 生成预签名URL
            return minioClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                                                                              .method(Http.Method.GET)
                                                                              .bucket(MinioBucket.MINIO_IMAGE_BUCKET)
                                                                              .object(key)
                                                                              .expiry(expireSeconds)
                                                                              .build());
        }
        catch (MinioException e)
        {
            throw new RuntimeException(e);
        }
    }

    /**
     * 探测指定bucket中的对象是否存在
     *
     * @param key 对象key
     * @return 对象是否存在
     */
    private boolean objectExists(String key)
    {
        try
        {
            minioClient.statObject(StatObjectArgs.builder().bucket(MinioBucket.MINIO_IMAGE_BUCKET).object(key).build());
            return true;
        }
        catch (ErrorResponseException e)
        {
            // 对象不存在
            return false;
        }
        catch (MinioException e)
        {
            throw new RuntimeException("探测对象是否存在失败: " + key, e);
        }
    }

}
