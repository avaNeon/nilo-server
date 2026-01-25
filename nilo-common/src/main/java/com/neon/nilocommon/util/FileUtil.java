package com.neon.nilocommon.util;

import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

/**
 * 文件处理工具类
 */
@Slf4j
public class FileUtil
{
    /**
     * 删除一个文件夹<hr/>
     * 使用NIO的Files类，能够在删除失败时打印错误信息
     *
     * @param file 文件对象
     */
    public static void deleteFolder(File file)
    {
        Path path = file.toPath();
        try (Stream <Path> stream = Files.walk(path))// 获得的Path stream是DFS顺序的各种Path，所以反序可以实现自底向上的删除操作
        {
            stream.sorted(Comparator.reverseOrder()).forEach(FileUtil::safeDelete);
        }
        catch (IOException e)
        {
            log.error("删除文件夹时错误");
            throw new RuntimeException(e);
        }
    }

    /**
     * 安全删除<hr/>
     * 可以在删除失败时产生异常，并且内部捕获了异常
     *
     * @param path Path类型变量
     */
    private static void safeDelete(Path path)
    {
        try
        {
            Files.delete(path);
        }
        catch (IOException e)
        {
            log.error("删除文件时错误");
            throw new RuntimeException(e);
        }
    }
}
