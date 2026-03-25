package com.neon.nilomqconsumer.consumer;

import com.neon.nilocommon.entity.constants.Constants;
import com.neon.nilocommon.entity.constants.MqInfo;
import com.neon.nilocommon.util.StringUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.FileSystemUtils;

import java.nio.file.Path;
import java.nio.file.Paths;

@Slf4j
@Component
public class FileDeleteConsumer
{
    private final String rootFilePathStr;

    public FileDeleteConsumer(@Value("${project.folder}") String rootPathStr)
    {
        rootFilePathStr = rootPathStr + "/" + Constants.FILE_FOLDER_NAME;
    }


    @RabbitListener(queues = MqInfo.STORAGE_DELETE_QUEUE)
    public void receiveMessage(String filePathStr)
    {
        try
        {
            // 检验路径合法性
            if (!StringUtil.isValidPath(filePathStr, rootFilePathStr))
            {
                log.warn("此路径\"{}\"不合法", filePathStr);
                return;
            }

            Path path = Paths.get(filePathStr);
            Path rootPath = Paths.get(rootFilePathStr);

            if (path.equals(rootPath))
            {
                log.error("检测到删除根目录指令，已阻止该操作");
                return;
            }

            // 递归删除非空的文件夹
            boolean isDeleted = FileSystemUtils.deleteRecursively(path);
            //todo 之后考虑把这部分日志删除了
            if (isDeleted) log.info("\"{}\"删除成功", filePathStr);
            else log.info("\"{}\"未删除，因为其不存在", filePathStr);

        }
        catch (Exception e)
        {
            throw new RuntimeException("文件删除失败", e);
        }
    }
}
