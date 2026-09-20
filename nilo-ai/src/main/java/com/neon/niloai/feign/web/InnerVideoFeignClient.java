package com.neon.niloai.feign.web;

import com.neon.nilocommon.entity.dto.VideoEmbedSourceDTO;
import com.neon.nilocommon.entity.vo.PaginationResponseVO;
import com.neon.nilocommon.entity.vo.ResponseVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(value = "nilo-web", contextId = "innerVideoFeignClient", path = "/inner/video")
public interface InnerVideoFeignClient
{
    /**
     * 分页拉取标题、标签、简介，用于向量灌入
     */
    @GetMapping("/embed-source")
    ResponseVO <PaginationResponseVO <VideoEmbedSourceDTO>> listEmbedSource(@RequestParam(name = "pageNo") Integer pageNo,
                                                                            @RequestParam(name = "pageSize") Integer pageSize);
}
