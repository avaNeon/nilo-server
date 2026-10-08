package com.neon.nilomqconsumer.consumer;

import com.neon.nilocommon.entity.constants.MqInfo;
import com.neon.nilocommon.entity.dto.mq.VideoDeleteDTO;
import com.neon.nilocommon.entity.enums.ResponseCode;
import com.neon.nilocommon.entity.vo.ResponseVO;
import com.neon.nilomqconsumer.feign.web.InnerVideoFeignClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * 删除视频消费者<hr/>
 * <p>用户在创作中心、管理员在后台删除视频时，请求端校验通过后发消息过来，这里调 nilo-web 执行真正的删除：
 * 归档、移动封面和视频文件、通知评论服务归档评论。这些操作很重，放在消费端，删除接口就不用等。</p>
 * <p>nilo-web 处理时会按当时的数据重新校验，不通过只记日志并返回成功，所以只有真正的故障（服务不可用、移动文件失败等）才会重试。</p>
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class VideoDeleteConsumer
{
    private final InnerVideoFeignClient innerVideoFeignClient;

    @RabbitListener(queues = MqInfo.VIDEO_DELETE_QUEUE)
    public void receiveMessage(VideoDeleteDTO dto)
    {
        if (dto == null || dto.getUserId() == null || dto.getVideoId() == null || dto.getDeleterType() == null)
        {
            throw new IllegalArgumentException("删除视频消息不完整，dto = " + dto);
        }

        ResponseVO <Void> result = innerVideoFeignClient.deleteVideo(dto);
        if (result == null || !ResponseCode.SUCCESS.getCode().equals(result.getCode()))
        {
            String detail = result == null ? "nilo-web 调用失败" : result.getInfo();
            throw new RuntimeException("删除视频失败，videoId = " + dto.getVideoId() + "，" + detail);
        }
    }
}
