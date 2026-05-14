package com.neon.nilomqconsumer.consumer;

import com.neon.nilocommon.entity.constants.Constants;
import com.neon.nilocommon.entity.constants.MqInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@RequiredArgsConstructor
@Component
public class FileDeleteConsumer
{
    private final FileDeleteService fileDeleteService;


    @RabbitListener(queues = MqInfo.STORAGE_DELETE_QUEUE)
    public void receiveMessage(String filePathStr)
    {
        fileDeleteService.delete(filePathStr);
    }
}
