package com.neon.nilostorage.service;

import com.neon.nilocommon.entity.constants.MinioBucket;
import com.neon.nilocommon.entity.constants.MinioKey;
import com.neon.nilocommon.entity.enums.ResponseCode;
import com.neon.nilocommon.exception.BusinessException;
import com.neon.nilocommon.util.FileUtil;
import io.minio.*;
import io.minio.errors.ErrorResponseException;
import io.minio.errors.MinioException;
import io.minio.messages.DeleteRequest;
import io.minio.messages.DeleteResult;
import io.minio.messages.Item;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class VideoFileService
{
    private final MinioClient minioClient;

    /**
     * 获取上传单个视频文件的 presigned post form
     *
     * @param key           minio key
     * @param expireSeconds 过期秒数
     * @param maxBytes      最大字节
     * @return presigned post form
     */
    public Map <String, String> upload(String key, long expireSeconds, long maxBytes)
    {
        PostPolicy postPolicy = new PostPolicy(MinioBucket.MINIO_VIDEO_BUCKET, ZonedDateTime.now().plusSeconds(expireSeconds));
        postPolicy.addEqualsCondition("key", key);
        postPolicy.addContentLengthRangeCondition(0L, maxBytes);
        // 限制只能上传视频类型的文件（MinIO弱校验客户端声明的Content-Type，无法校验实际类型）
        postPolicy.addStartsWithCondition("Content-Type", "video/");

        try
        {
            return minioClient.getPresignedPostFormData(postPolicy);
        }
        catch (MinioException e)
        {
            throw new RuntimeException(e);
        }
    }

    /**
     * 移动视频文件的key<hr/>
     * <p>此操作幂等、可重入，不保证原子性</p>
     * <p>移动前会先探测目标key是否已存在：如果已存在，说明之前的移动已经生效（可能是重试请求），直接跳过，不再报错</p>
     * <p>只有当源key和目标key都不存在时，才认为是真正的异常状态并抛出异常</p>
     *
     * @param srcKey  视频文件的原key
     * @param destKey 视频文件的新key
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
                                                 .bucket(MinioBucket.MINIO_VIDEO_BUCKET)
                                                 .object(destKey)
                                                 .source(SourceObject.builder()
                                                                     .bucket(MinioBucket.MINIO_VIDEO_BUCKET)
                                                                     .object(srcKey)
                                                                     .build())
                                                 .build());

            // 再删除原始key
            minioClient.removeObject(RemoveObjectArgs.builder().bucket(MinioBucket.MINIO_VIDEO_BUCKET).object(srcKey).build());
        }
        catch (MinioException e)
        {
            throw new RuntimeException("移动视频文件失败: " + srcKey + " -> " + destKey, e);
        }
    }

    /**
     * 批量移动视频文件的key<hr/>
     * 此操作幂等、可重入，不保证原子性
     *
     * @param keyMap key映射表，key为原始key，value为目标key
     */
    public void batchMove(Map <String, String> keyMap)
    {
        keyMap.forEach(this::move);
    }

    /**
     * 移动目录前缀下的所有对象（含子对象）到目标目录前缀下，保持相对路径不变<hr/>
     * <ul>
     *     <li>源目录无对象、目标目录有对象：视为已发布，直接跳过</li>
     *     <li>源目录与目标目录均无对象：抛出 {@link ResponseCode#NOT_FOUND}</li>
     * </ul>
     *
     * @param srcPrefixRoot  源目录在 MinIO 中的完整前缀（如 {@code pending/foo/bar}）
     * @param destPrefixRoot 目标目录在 MinIO 中的完整前缀（如 {@code public/foo/bar}）
     */
    public void moveDirectory(String srcPrefixRoot, String destPrefixRoot)
    {
        try
        {
            // 查出源前缀下的所有文件
            List <String> srcKeys = listObjectKeys(srcPrefixRoot);
            // 如果没有，查目标前缀下的所有文件
            if (srcKeys.isEmpty())
            {
                // 如果也没有，说明不存在文件，报错
                if (listObjectKeys(destPrefixRoot).isEmpty())
                {
                    throw new BusinessException(ResponseCode.NOT_FOUND);
                }
                // 否则应该是已经都传过去了，不排除原前缀下中有缺失文件，但是我们在这里已经做了这个微服务所有该做的事情了，可以返回了
                return;
            }

            Map <String, String> keyMap = new java.util.LinkedHashMap <>();
            for (String srcKey : srcKeys)
            {
                keyMap.put(srcKey, destPrefixRoot + srcKey.substring(srcPrefixRoot.length()));
            }
            batchMove(keyMap);
        }
        catch (BusinessException e)
        {
            throw e;
        }
        catch (Exception e)
        {
            throw new RuntimeException("移动视频目录失败: " + srcPrefixRoot + " -> " + destPrefixRoot, e);
        }
    }

    /**
     * 批量移动多个目录前缀下的对象
     *
     * @param directoryMap key 为源目录前缀，value 为目标目录前缀
     */
    public void batchMoveDirectory(Map <String, String> directoryMap)
    {
        if (directoryMap == null || directoryMap.isEmpty())
        {
            return;
        }

        directoryMap.forEach(this::moveDirectory);
    }

    /**
     * 递归删除视频文件<hr/>
     * <p>此操作具有幂等性，经过<b>实测</b>删除不存在的文件不会报错</p>
     * <p>会删除 tmp/pending/public 三个前缀下的源文件对象，以及各前缀下以 baseKey 为目录的转码产物</p>
     *
     * @param baseKey 文件在MinIO中的<b>不带前缀</b>的key
     */
    public void deleteRecursively(String baseKey)
    {
        try
        {
            List <DeleteRequest.Object> objects = new ArrayList <>();
            objects.addAll(collectObjectAndDescendants(MinioKey.TMP_PREFIX + baseKey));
            objects.addAll(collectObjectAndDescendants(MinioKey.PENDING_PREFIX + baseKey));
            objects.addAll(collectObjectAndDescendants(MinioKey.PUBLIC_PREFIX + baseKey));
            deleteObjects(objects);
        }
        catch (Exception e)
        {
            throw new RuntimeException("递归删除视频文件失败: " + baseKey, e);
        }
    }

    /**
     * 批量递归删除视频文件<hr/>
     * 收集所有baseKey在三个前缀下的对象及转码产物，一次性批量删除
     *
     * @param baseKeys 不带前缀的minio key列表
     */
    public void batchDeleteRecursively(List <String> baseKeys)
    {
        if (baseKeys == null || baseKeys.isEmpty())
        {
            return;
        }

        List <DeleteRequest.Object> objects = new ArrayList <>();
        for (String baseKey : baseKeys)
        {
            try
            {
                objects.addAll(collectObjectAndDescendants(MinioKey.TMP_PREFIX + baseKey));
                objects.addAll(collectObjectAndDescendants(MinioKey.PENDING_PREFIX + baseKey));
                objects.addAll(collectObjectAndDescendants(MinioKey.PUBLIC_PREFIX + baseKey));
            }
            catch (Exception e)
            {
                log.error("收集待删除视频文件失败, baseKey={}", baseKey, e);
            }
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
     * 删除单个视频对象<hr/>
     * <p>仅删除指定完整 key 对应的对象本身，不会删除以该key为前缀的子对象</p>
     * <p>此操作具有幂等性，删除不存在的对象不会报错</p>
     *
     * @param key 文件在MinIO中的完整key（须带前缀）
     */
    public void deleteObject(String key)
    {
        try
        {
            minioClient.removeObject(RemoveObjectArgs.builder().bucket(MinioBucket.MINIO_VIDEO_BUCKET).object(key).build());
        }
        catch (Exception e)
        {
            throw new RuntimeException("删除视频对象失败: " + key, e);
        }
    }

    /**
     * 请求用于下载 pending 状态 HLS 视频文件的 presigned url
     *
     * @param key           文件在MinIO中的key（须带 pending/ 前缀）
     * @param expireSeconds 过期时间（秒）
     * @return 预签名URL
     */
    public String downloadVideo(String key, int expireSeconds)
    {
        if (!FileUtil.isValidVideoHlsObjectKey(key))
        {
            throw new BusinessException(ResponseCode.INVALID_ARGUMENTS);
        }

        try
        {
            return minioClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                                                                              .method(Http.Method.GET)
                                                                              .bucket(MinioBucket.MINIO_VIDEO_BUCKET)
                                                                              .object(key)
                                                                              .expiry(expireSeconds)
                                                                              .build());
        }
        catch (ErrorResponseException e)
        {
            throw new BusinessException(ResponseCode.NOT_FOUND);
        }
        catch (MinioException e)
        {
            throw new RuntimeException(e);
        }
    }

    /**
     * 获取视频文件在MinIO中的文件大小
     *
     * @param key 文件在MinIO中的key
     * @return 文件大小（字节）
     */
    public long getVideoFileSize(String key)
    {
        try
        {
            StatObjectResponse stat = minioClient.statObject(StatObjectArgs.builder()
                                                                           .bucket(MinioBucket.MINIO_VIDEO_BUCKET)
                                                                           .object(key)
                                                                           .build());
            return stat.size();
        }
        catch (ErrorResponseException e)
        {
            throw new BusinessException(ResponseCode.NOT_FOUND);
        }
        catch (MinioException e)
        {
            throw new BusinessException(ResponseCode.UNKNOWN_ERROR);
        }
    }

    /**
     * 探测视频文件在三个前缀（tmp/、pending/、public/）下的文件大小
     * <p>
     * 业务规则：
     * <ul>
     *   <li>3个前缀都查不到：抛出 NOT_FOUND（合法状态）</li>
     *   <li>恰好1个前缀查到：返回该文件大小（合法状态）</li>
     *   <li>2个或3个前缀都查到，或MinIO异常：抛出 UNKNOWN_ERROR</li>
     * </ul>
     *
     * @param baseKey 不带前缀的视频文件key
     * @return 文件大小（字节）
     */
    public long probeVideoFileSize(String baseKey)
    {
        String[] keys = {MinioKey.TMP_PREFIX + baseKey, MinioKey.PENDING_PREFIX + baseKey, MinioKey.PUBLIC_PREFIX + baseKey};

        Long fileSize = null;
        int foundCount = 0;

        for (String key : keys)
        {
            try
            {
                StatObjectResponse stat = minioClient.statObject(StatObjectArgs.builder()
                                                                               .bucket(MinioBucket.MINIO_VIDEO_BUCKET)
                                                                               .object(key)
                                                                               .build());
                foundCount++;
                fileSize = stat.size();
            }
            catch (ErrorResponseException e)
            {
                // 文件不存在，继续检查下一个前缀
            }
            catch (MinioException e)
            {
                // MinIO异常（非"不存在"），属于未知错误
                throw new BusinessException(ResponseCode.UNKNOWN_ERROR);
            }
        }

        if (foundCount == 0)
        {
            throw new BusinessException(ResponseCode.NOT_FOUND);
        }

        if (foundCount == 1)
        {
            return fileSize;
        }

        // 2个或3个前缀都查到了，属于非法状态
        throw new BusinessException(ResponseCode.UNKNOWN_ERROR);
    }

    /**
     * 收集指定 key 对应的对象及其所有子对象
     *
     * @param keyOrPrefix key或前缀
     * @return 待删除对象列表
     */
    private List <DeleteRequest.Object> collectObjectAndDescendants(String keyOrPrefix) throws Exception
    {
        List <DeleteRequest.Object> objects = new ArrayList <>();
        for (String objectKey : listObjectKeys(keyOrPrefix))
        {
            objects.add(new DeleteRequest.Object(objectKey));
        }
        return objects;
    }

    /**
     * 列出指定 key 及其所有子对象的完整 minio key
     */
    private List <String> listObjectKeys(String keyOrPrefix) throws Exception
    {
        List <String> keys = new ArrayList <>();

        if (objectExists(keyOrPrefix))
        {
            keys.add(keyOrPrefix);
        }

        String descendantPrefix = keyOrPrefix.endsWith("/") ? keyOrPrefix : keyOrPrefix + "/";
        Iterable <Result <Item>> results = minioClient.listObjects(ListObjectsArgs.builder()
                                                                                  .bucket(MinioBucket.MINIO_VIDEO_BUCKET)
                                                                                  .prefix(descendantPrefix)
                                                                                  .recursive(true)
                                                                                  .build());
        for (Result <Item> result : results)
        {
            keys.add(result.get().objectName());
        }

        return keys;
    }

    /**
     * 批量删除objects
     *
     * @param objects 文件列表
     * @throws MinioException minio异常
     */
    private void deleteObjects(List <DeleteRequest.Object> objects) throws MinioException
    {
        if (objects == null || objects.isEmpty())
        {
            return;
        }

        Iterable <Result <DeleteResult.Error>> errors = minioClient.removeObjects(RemoveObjectsArgs.builder()
                                                                                                   .bucket(MinioBucket.MINIO_VIDEO_BUCKET)
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
            throw new RuntimeException("批量删除失败");
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
            minioClient.statObject(StatObjectArgs.builder().bucket(MinioBucket.MINIO_VIDEO_BUCKET).object(key).build());
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
