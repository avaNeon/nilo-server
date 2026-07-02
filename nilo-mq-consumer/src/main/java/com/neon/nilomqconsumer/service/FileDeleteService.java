package com.neon.nilomqconsumer.service;

import com.neon.nilocommon.entity.constants.Constants;
import com.neon.nilocommon.util.StringUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.FileSystemUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Slf4j
@Service
public class FileDeleteService
{
    private final String rootFilePathStr;

    public FileDeleteService(@Value("${project.folder}") String rootPathStr)
    {
        this.rootFilePathStr = rootPathStr + "/" + Constants.FILE_FOLDER_NAME;
    }

    /**
     * 删除指定路径下的文件<hr/>
     * 除非删除文件遇到操作系统级问题，否则不会报错
     *
     * @param filePathStr 文件路径
     */
    public void delete(String filePathStr)
    {
        try
        {
            if (!StringUtil.isValidPath(rootFilePathStr, filePathStr))
            {
                log.warn("此路径\"{}\"不合法", filePathStr);
                return;
            }

            Path path = Paths.get(filePathStr).normalize();
            Path rootPath = Paths.get(rootFilePathStr).normalize();

            if (path.equals(rootPath))
            {
                log.error("检测到删除根目录指令，已阻止该操作");
                return;
            }

            if (!Files.exists(path))
            {
                log.info("\"{}\"未删除，因为其不存在", filePathStr);
                return;
            }

            boolean isDeleted = FileSystemUtils.deleteRecursively(path);
            if (isDeleted) log.info("\"{}\"删除成功", filePathStr);
            else log.info("\"{}\"未删除，因为其不存在", filePathStr);
        }
        catch (Exception e)
        {
            throw new RuntimeException("文件删除失败", e);
        }
    }
}
