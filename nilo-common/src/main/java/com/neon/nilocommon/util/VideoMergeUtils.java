package com.neon.nilocommon.util;

import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Comparator;


@Slf4j
public class VideoMergeUtils
{
    /**
     * 合并分片文件
     *
     * @param chunkFolderPath 分片所在的文件夹路径 (例如: /data/tmp/upload/uuid_folder)
     * @param targetFile      合并后文件的完整路径 (例如: /data/videos/final.mp4)
     * @param delChunks       合并成功后是否删除分片
     */
    public static void mergeChunks(String chunkFolderPath, String targetFile, boolean delChunks) throws IOException
    {
        Path chunkDir = Paths.get(chunkFolderPath);
        Path target = Paths.get(targetFile);

        // 1. 校验分片目录是否存在
        if (!Files.exists(chunkDir) || !Files.isDirectory(chunkDir))
        {
            throw new IOException("分片目录不存在: " + chunkFolderPath);
        }

        // 2. 获取所有分片文件
        // 过滤掉非数字命名的文件（比如 .DS_Store 或 Thumbs.db），防止报错
        File[] chunks = chunkDir.toFile().listFiles(f -> f.isFile() && f.getName().matches("\\d+"));

        if (chunks == null || chunks.length == 0)
        {
            throw new IOException("目录中没有可合并的分片文件");
        }

        // 3. 关键步骤：按文件名数字大小排序 (1, 2, 3... 10)
        Arrays.sort(chunks, Comparator.comparingInt(f -> Integer.parseInt(f.getName())));

        // 4. 确保目标文件的父目录存在
        if (target.getParent() != null && !Files.exists(target.getParent()))
        {
            Files.createDirectories(target.getParent());
        }

        log.info("开始合并 {} 个分片到 {}", chunks.length, targetFile);

        // 5. 使用 FileChannel 进行零拷贝合并
        // try-with-resources 自动关闭资源
        try (FileChannel destChannel = FileChannel.open(target, StandardOpenOption.CREATE, StandardOpenOption.WRITE))
        {
            for (File chunk : chunks)
            {
                try (FileChannel srcChannel = FileChannel.open(chunk.toPath(), StandardOpenOption.READ))
                {
                    // transferFrom 是零拷贝的核心，直接在内核态传输数据
                    // pos: destChannel.size() 表示追加到文件末尾
                    destChannel.transferFrom(srcChannel, destChannel.size(), srcChannel.size());
                }
            }
        }

        log.info("合并完成: {}", targetFile);

        // 6. (可选) 清理分片目录
        if (delChunks)
        {
            // 使用简单的递归删除
            for (File chunk : chunks)
            {
                if (!chunk.delete())
                {
                    throw new RuntimeException("删除失败");
                }
            }
            log.info("已清理分片目录: {}", chunkFolderPath);
        }
    }
}
