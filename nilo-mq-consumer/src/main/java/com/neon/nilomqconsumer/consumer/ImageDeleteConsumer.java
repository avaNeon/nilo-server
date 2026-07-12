package com.neon.nilomqconsumer.consumer;

import com.neon.nilocommon.entity.constants.MqInfo;
import com.neon.nilocommon.entity.vo.ResponseVO;
import com.neon.nilomqconsumer.feign.storage.ImageFeignClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@RequiredArgsConstructor
@Component
public class ImageDeleteConsumer
{
    private final ImageFeignClient imageFeignClient;

    @RabbitListener(queues = MqInfo.STORAGE_IMAGE_DELETE_QUEUE)
    public void receiveMessage(List <String> baseKeys)
    {
        ResponseVO <Void> responseVO = imageFeignClient.batchDelete(baseKeys);
        if (!responseVO.getStatus().equals(ResponseVO.STATUS_SUCCESS))
        {
            throw new RuntimeException("文件删除失败，keys = " + baseKeys);
        }
    }
}
