package com.neon.nilomqconsumer.consumer;

import com.neon.nilocommon.entity.constants.Constants;
import com.neon.nilocommon.entity.constants.MqInfo;
import com.neon.nilocommon.entity.constants.RedisKey;
import com.neon.nilocommon.entity.dto.UploadedVideoFileDTO;
import com.neon.nilocommon.entity.po.VideoInfoFileUpload;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;

@Slf4j
@RequiredArgsConstructor
@Component
public class VideoTransCodingConsumer
{

    private final RedisTemplate <String, Object> redisTemplate;

    @Value("${project.folder}")
    private String rootPath;

    @RabbitListener(queues = MqInfo.STORAGE_TRANSCODING_QUEUE)
    public void receiveMessage(VideoInfoFileUpload fileUpload, Channel channel, @Header(AmqpHeaders.DELIVERY_TAG) long tag)
    {
        try
        {
            UploadedVideoFileDTO fileDTO = (UploadedVideoFileDTO) redisTemplate.opsForValue()
                                                                               .get(RedisKey.PRE_UPLOADED_VIDEO_TAG_PREFIX + fileUpload.getUserId() + ":" + fileUpload.getUploadId());
            if (fileDTO == null) throw new RuntimeException("未找到转码文件的记录");

            String from = Paths.get(rootPath, Constants.FILE_FOLDER_NAME, Constants.TMP_FOLDER_NAME, fileDTO.getFilePath()).toString();
            File srcFile = new File(from);
            String to = Paths.get(rootPath, Constants.FILE_FOLDER_NAME, Constants.VIDEO_FOLDER_NAME, fileDTO.getFilePath()).toString();
            File destFile = new File(to);

            // 移动视频文件
            if (destFile.exists()) FileUtils.deleteDirectory(destFile); // 如果目标目录存在，那就直接删除避免报错
            FileUtils.moveDirectory(srcFile, destFile);

            // 删除在redis的记录
            redisTemplate.delete(RedisKey.PRE_UPLOADED_VIDEO_TAG_PREFIX + fileUpload.getUserId() + ":" + fileUpload.getUploadId());

            // todo 文件合并

            channel.basicAck(tag, false);
        }
        catch (Exception e)
        {
            try
            {
                channel.basicNack(tag, false, true);
            }
            catch (IOException ex)
            {
                log.error("消息拒绝时发生失败！");
                throw new RuntimeException(ex);
            }
        }
    }

}
