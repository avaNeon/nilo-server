package com.neon.niloweb.controller.inner;

import com.neon.nilocommon.entity.dto.CommentMessageDTO;
import com.neon.nilocommon.entity.vo.ResponseVO;
import com.neon.niloweb.service.UserMessageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "内部-用户消息接口", description = "仅供服务间调用")
@Validated
@RequiredArgsConstructor
@RequestMapping("/inner/message")
@RestController
public class InnerUserMessageController
{
    private final UserMessageService userMessageService;

    @Operation(summary = "发送评论通知")
    @PostMapping("/comment")
    public ResponseVO <Void> sendCommentMessage(@RequestBody @Valid CommentMessageDTO request)
    {
        // @Async 在 web 侧异步执行，此处立即返回
        userMessageService.sendCommentMessage(request.getReceiverUserId(),
                                              request.getSenderUserId(),
                                              request.getVideoId(),
                                              request.getCommentContent(),
                                              request.getReplyCommentContent());
        return ResponseVO.success();
    }
}
