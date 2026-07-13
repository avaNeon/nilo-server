package com.neon.nilomqconsumer.consumer;

import com.neon.nilocommon.entity.constants.MqInfo;
import com.neon.nilocommon.entity.vo.ResponseVO;
import com.neon.nilomqconsumer.feign.storage.InnerVideoFileFeignClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@RequiredArgsConstructor
@Component
public class VideoFileDeleteConsumer
{
    private final InnerVideoFileFeignClient innerVideoFileFeignClient;

    @RabbitListener(queues = MqInfo.STORAGE_VIDEO_DELETE_QUEUE)
    public void receiveMessage(String key)
    {
        ResponseVO <Void> responseVO = innerVideoFileFeignClient.deleteRecursively(key);
        if (!responseVO.getStatus().equals(ResponseVO.STATUS_SUCCESS))
        {
            throw new RuntimeException("文件删除失败，key = " + key);
        }
    }
}
