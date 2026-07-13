package com.neon.nilocomment.feign.web;

import com.neon.nilocommon.entity.dto.VideoSnapshotDTO;
import com.neon.nilocommon.entity.vo.ResponseVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(value = "nilo-web", contextId = "innerVideoFeignClient", path = "/inner/video")
public interface InnerVideoFeignClient
{
    @GetMapping("/{videoId}")
    ResponseVO <VideoSnapshotDTO> getVideoSnapshot(@PathVariable(name = "videoId") Long videoId);

    @PostMapping("/{videoId}/comment/count/increase")
    ResponseVO <Void> increaseCommentCount(@PathVariable(name = "videoId") Long videoId,
                                           @RequestParam(name = "delta", defaultValue = "1") Integer delta);

    @PostMapping("/{videoId}/comment/count/decrease")
    ResponseVO <Void> decreaseCommentCount(@PathVariable(name = "videoId") Long videoId,
                                           @RequestParam(name = "delta", defaultValue = "1") Integer delta);
}
