package com.neon.niloai.feign.web;

import com.neon.nilocommon.entity.vo.ResponseVO;
import com.neon.nilocommon.entity.vo.videoInfoDoc.VideoSearchResultVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(value = "nilo-web", contextId = "innerVideoSearchFeignClient", path = "/inner/video-search")
public interface InnerVideoSearchFeignClient
{
    /**
     * 调用 nilo-web 内部关键词搜索
     */
    @GetMapping
    ResponseVO <VideoSearchResultVO> searchVideo(@RequestParam(name = "keyword") String keyword,
                                                 @RequestParam(name = "pageSize") Integer pageSize);
}
