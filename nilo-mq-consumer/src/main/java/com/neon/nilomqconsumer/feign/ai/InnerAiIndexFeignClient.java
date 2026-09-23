package com.neon.nilomqconsumer.feign.ai;

import com.neon.nilocommon.entity.vo.ResponseVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

@FeignClient(value = "nilo-ai", contextId = "innerAiIndexFeignClient", path = "/inner/index")
public interface InnerAiIndexFeignClient
{
    @PostMapping("/video/{videoId}")
    ResponseVO <Integer> indexVideo(@PathVariable(name = "videoId") Long videoId);

    @DeleteMapping("/video/{videoId}")
    ResponseVO <Void> deleteVideo(@PathVariable(name = "videoId") Long videoId);

    @PostMapping("/subtitle/{videoId}")
    ResponseVO <Integer> indexSubtitle(@PathVariable(name = "videoId") Long videoId);
}
