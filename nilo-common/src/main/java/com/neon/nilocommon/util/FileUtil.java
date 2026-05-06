package com.neon.nilocommon.util;

import com.neon.nilocommon.entity.constants.Constants;
import com.neon.nilocommon.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
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
     * 校验文件是否合法且存在
     *
     * @param rootPathStr 文件根路径
     * @param filePathStr 文件相对路径
     * @return 是否合法存在
     */
    public static boolean fileExists(String rootPathStr, String filePathStr)
    {
        // 首先，应该不是空
        if (filePathStr == null || filePathStr.trim().isEmpty())
        {
            return false;
        }
        Path path = Paths.get(rootPathStr, filePathStr);
        // 然后，必须合法，不能离开指定目录
        if (!StringUtil.isValidPath(path.toString(), Paths.get(rootPathStr).toString()))
        {
            return false;
        }
        // 最后校验是否存在，是否是一个文件而非目录
        return Files.exists(path) && Files.isRegularFile(path);
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

    /**
     * 验证图片是否存在，并将其移动 tmp -> cover
     *
     * @param rootPathStr  项目根路径
     * @param coverPathStr 封面图片相对路径
     */
    public static void verifyAndMoveCover(String rootPathStr, String coverPathStr)
    {
        // 先把封面移动到COVER文件夹
        Path rootPath = Path.of(rootPathStr, Constants.FILE_FOLDER_NAME, Constants.TMP_FOLDER_NAME);
        Path coverAbsPath = Path.of(rootPath.toString(), coverPathStr);
        if (fileExists(rootPath.toString(), coverPathStr))
        {
            Path destPath = Path.of(rootPathStr, Constants.FILE_FOLDER_NAME, Constants.COVER_FOLDER_NAME, coverPathStr);
            try
            {
                // 确保目标目录存在
                Files.createDirectories(destPath.getParent());
                // 删除目标位置源文件
                if (Files.exists(destPath))
                {
                    if (Files.isRegularFile(destPath) || Files.isSymbolicLink(destPath))
                    {
                        Files.deleteIfExists(destPath);
                    }
                    else if (Files.isDirectory(destPath))
                    {
                        FileUtils.deleteDirectory(destPath.toFile());
                    }
                    else
                    {
                        throw new RuntimeException("未知的文件格式");
                    }
                }
                Files.move(coverAbsPath, destPath, StandardCopyOption.REPLACE_EXISTING);
            }
            catch (IOException e)
            {
                throw new RuntimeException(e);
            }
        }
        else
        {
            // TODO TMP文件清理速度尽量快于预上传视频key的清理速度，因为预上传视频的key总比视频封面产生早，这样如果触发到这条异常说明用户正在做出不正常的行为
            throw new BusinessException("图片资源不存在");
        }
    }
}
