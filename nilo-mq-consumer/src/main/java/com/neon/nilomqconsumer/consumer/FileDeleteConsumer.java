package com.neon.nilomqconsumer.consumer;

import com.neon.nilocommon.entity.constants.Constants;
import com.neon.nilocommon.entity.constants.MqInfo;
import com.neon.nilocommon.util.StringUtil;
import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import org.springframework.util.FileSystemUtils;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

@Slf4j
@Component
public class FileDeleteConsumer
{
    // 只允许删除临时文件
    private final String deletePathPrefix;

    public FileDeleteConsumer(@Value("${project.folder}") String rootPath)
    {
        deletePathPrefix = rootPath + "/" + Constants.FILE_FOLDER_NAME + "/" + Constants.TMP_FOLDER_NAME;
    }


    @RabbitListener(queues = MqInfo.STORAGE_DELETE_QUEUE)
    public void receiveMessage(String filePath, Channel channel, @Header(AmqpHeaders.DELIVERY_TAG) long tag)
    {
        try
        {
            log.info("删除路径为\"{}\"的文件", filePath);
            // 检验路径合法性
            if (!StringUtil.isValidPath(filePath, deletePathPrefix))
            {
                log.warn("此路径\"{}\"不合法", filePath);
                channel.basicAck(tag, false);
                return;
            }

            Path path = Paths.get(filePath);
            Path rootPath = Paths.get(deletePathPrefix);

            if (path.equals(rootPath))
            {
                log.error("检测到删除根目录指令，已阻止该操作");
                channel.basicAck(tag, false);
                return;
            }

            //递归删除非空的文件夹
            boolean isDeleted = FileSystemUtils.deleteRecursively(path);
            if (isDeleted) log.info("\"{}\"删除成功", filePath);
            else log.info("\"{}\"未删除，因为其不存在", filePath);

            channel.basicAck(tag, false);
        }
        catch (Exception e)
        {
            log.error("tag为\"{}\"的消息消费失败, 原因: {}", tag, e.getMessage());
            try
            {
                channel.basicNack(tag, false, false);
            }
            catch (IOException ex)
            {
                log.error("消息拒绝时发生失败！");
                throw new RuntimeException(ex);
            }
        }
    }
}
