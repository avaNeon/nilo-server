package com.neon.nilomqconsumer.service;

import com.neon.nilocommon.entity.constants.MinioBucket;
import com.neon.nilomqconsumer.config.properties.CustomMinioProperties;
import io.minio.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

@Slf4j
@RequiredArgsConstructor
@Service
public class FileService
{
    private final MinioClient minioClient;

    private final CustomMinioProperties minioProperties;

    /**
     * 将MinIO中的视频文件下载到本地<hr/>
     * <p>如果目标路径已经存在文件/目录，会直接覆盖</p>
     *
     * @param key      文件在MinIO中的key
     * @param savePath 下载后的本地文件保存路径，不能为null
     */
    public void downloadVideoFile(String key, Path savePath)
    {
        // 先检查目标路径是否存在文件/目录
        if (!Files.exists(savePath))
        {
            // 如果不存在，先检查下是否存在父目录，防止不存在父目录后续下载文件出错
            Path parentPath = savePath.getParent();
            if (parentPath != null && !Files.exists(parentPath))
            {
                try
                {
                    Files.createDirectories(parentPath);
                }
                catch (IOException e)
                {
                    throw new RuntimeException("创建父目录失败: " + parentPath, e);
                }
            }
        }

        // 如果目标路径已存在且是目录，递归删除
        if (Files.isDirectory(savePath))
        {
            try (Stream <Path> walkStream = Files.walk(savePath))
            {
                walkStream.sorted(Comparator.reverseOrder()).forEach(p ->
                                                                     {
                                                                         try
                                                                         {
                                                                             Files.deleteIfExists(p);
                                                                         }
                                                                         catch (IOException e)
                                                                         {
                                                                             log.warn("删除文件时失败，错误详情：", e);
                                                                             throw new RuntimeException(e);
                                                                         }
                                                                     });
            }
            catch (Exception e)
            {
                throw new RuntimeException("递归删除目标路径失败: " + savePath, e);
            }
        }

        try
        {
            try (InputStream is = minioClient.getObject(GetObjectArgs.builder()
                                                                     .bucket(MinioBucket.MINIO_VIDEO_BUCKET)
                                                                     .object(key)
                                                                     .build()))
            {
                Files.copy(is, savePath, StandardCopyOption.REPLACE_EXISTING);
            }

            log.info("视频文件下载到本地成功, key={}, localPath={}", key, savePath);
        }
        catch (Exception e)
        {
            throw new RuntimeException("下载视频文件到本地失败: " + key, e);
        }
    }

    /**
     * 将本地目录下的所有文件递归上传到MinIO<hr/>
     * <p>MinIO中的key由{@code keyPrefix}与文件相对于{@code localDir}的相对路径拼接而成，
     * 例如{@code localDir}下的{@code 720P/index.m3u8}会上传到{@code keyPrefix + "/720P/index.m3u8"}</p>
     *
     * @param localDir  本地目录，必须存在且是目录
     * @param keyPrefix MinIO中的原始key前缀（结尾不带斜杠）
     */
    public void uploadDirectory(Path localDir, String keyPrefix)
    {
        // 检查待上传目录是否存在，必须是目录
        if (!Files.isDirectory(localDir))
        {
            throw new RuntimeException("待上传目录不存在: " + localDir);
        }

        List <Path> fileList;
        try (Stream <Path> walkStream = Files.walk(localDir))
        {
            fileList = walkStream.filter(Files::isRegularFile).toList();
        }
        catch (IOException e)
        {
            throw new RuntimeException("遍历待上传目录失败: " + localDir, e);
        }

        for (Path file : fileList)
        {
            // 获取文件相对于待上传目录的相对路径，注意使用NIO的时候要注意替换Windows风格的反斜杠为正斜杠
            String relativePath = localDir.relativize(file).toString().replace('\\', '/');
            String key = keyPrefix + "/" + relativePath;
            uploadVideoFile(file, key);
        }

        log.info("目录上传到MinIO成功, localDir={}, keyPrefix={}, fileCount={}", localDir, keyPrefix, fileList.size());
    }

    /**
     * 将单个本地文件上传到MinIO<hr/>
     * <p>使用 {@code uploadObject}（按本地文件路径上传），避免 MinIO Java 9 对
     * {@code putObject(stream)} 在部分场景下“调用成功但对象未落盘”的问题。
     * 上传后立即 {@code statObject} 校验，确保对象真实存在且大小一致。</p>
     *
     * @param file 本地文件
     * @param key  MinIO中的key
     */
    private void uploadVideoFile(Path file, String key)
    {
        try
        {
            long localSize = Files.size(file);

            ObjectWriteResponse writeResponse = minioClient.uploadObject(UploadObjectArgs.builder()
                                                                                         .bucket(MinioBucket.MINIO_VIDEO_BUCKET)
                                                                                         .object(key)
                                                                                         .filename(file.toAbsolutePath()
                                                                                                       .toString())
                                                                                         .contentType(resolveContentType(file))
                                                                                         .build());

            if (writeResponse == null)
            {
                throw new RuntimeException("上传视频文件到MinIO返回空响应: " + key);
            }

            StatObjectResponse stat = minioClient.statObject(StatObjectArgs.builder()
                                                                           .bucket(MinioBucket.MINIO_VIDEO_BUCKET)
                                                                           .object(key)
                                                                           .build());
            if (stat.size() != localSize)
            {
                throw new RuntimeException("上传后对象大小不一致, key=" + key + ", localSize=" + localSize + ", remoteSize=" + stat.size());
            }

            // 若这里能打出来且 stat 通过，对象一定已在「当前 MinioClient 的 endpoint」上；
            // 控制台看不到时，优先核对是不是查了另一台 MinIO / 另一个 bucket / 看错了前缀
            log.info("上传视频文件成功, endpoint={}, bucket={}, key={}, size={}, etag={}, region={}",
                     minioProperties.getEndpoint(),
                     MinioBucket.MINIO_VIDEO_BUCKET,
                     key,
                     localSize,
                     writeResponse.etag(),
                     writeResponse.region());
        }
        catch (Exception e)
        {
            throw new RuntimeException("上传视频文件到MinIO失败: " + key, e);
        }
    }

    /**
     * 根据文件后缀推断上传到MinIO时使用的Content-Type
     *
     * @param file 本地文件
     * @return Content-Type
     */
    private String resolveContentType(Path file)
    {
        String name = file.getFileName().toString();
        if (name.endsWith(".m3u8"))
        {
            return "application/vnd.apple.mpegurl";
        }
        if (name.endsWith(".ts"))
        {
            return "video/mp2t";
        }
        return "application/octet-stream";
    }
}
