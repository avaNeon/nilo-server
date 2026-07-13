package com.neon.nilocomment.feign.web;

import com.neon.nilocommon.entity.dto.CommentMessageDTO;
import com.neon.nilocommon.entity.vo.ResponseVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(value = "nilo-web", contextId = "innerUserMessageFeignClient", path = "/inner/message")
public interface InnerUserMessageFeignClient
{
    @PostMapping("/comment")
    ResponseVO <Void> sendCommentMessage(@RequestBody CommentMessageDTO request);
}
