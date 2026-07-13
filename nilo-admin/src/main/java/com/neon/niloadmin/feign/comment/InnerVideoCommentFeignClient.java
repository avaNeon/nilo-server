package com.neon.niloadmin.feign.comment;

import com.neon.nilocommon.entity.vo.ResponseVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

@FeignClient(value = "nilo-comment", contextId = "innerVideoCommentFeignClient", path = "/inner/comment")
public interface InnerVideoCommentFeignClient
{
    @PutMapping("/video/{videoId}/name")
    ResponseVO <Void> updateVideoNameByVideoId(@PathVariable(name = "videoId") Long videoId,
                                               @RequestParam(name = "videoName") String videoName);

    @PutMapping("/video/{videoId}/cover")
    ResponseVO <Void> updateVideoCoverByVideoId(@PathVariable(name = "videoId") Long videoId,
                                                @RequestParam(name = "videoCover") String videoCover);
}
