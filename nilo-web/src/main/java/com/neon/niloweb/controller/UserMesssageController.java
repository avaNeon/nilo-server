package com.neon.niloweb.controller;

import com.neon.nilocommon.entity.dto.UserMessageCount;
import com.neon.nilocommon.entity.dto.UserMessageDTO;
import com.neon.nilocommon.entity.vo.ResponseVO;
import com.neon.niloweb.loginState.LoginState;
import com.neon.niloweb.annotation.Authorized;
import com.neon.niloweb.service.UserMessageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "消息管理")
@Validated
@RequestMapping(path = "/message")
@RequiredArgsConstructor
@RestController
public class UserMesssageController
{
    private final UserMessageService userMessageService;

    private final LoginState loginState;

    @Operation(summary = "获取用户未读信息数量")
    @GetMapping("/unchecked")
    @Authorized
    public ResponseVO <UserMessageCount> getUncheckedMessageCount(@RequestHeader(name = "token") @NotNull String token)
    {
        return ResponseVO.success(userMessageService.getUncheckedMessageCount(loginState.getLoginUserId(token)));
    }

    @Operation(summary = "将一个分类的未读消息都标记为已读")
    @PostMapping("/clear/{messageType}")
    @Authorized
    public ResponseVO <Object> checkAllMessages(@RequestHeader(name = "token") @NotNull String token,
                                                @PathVariable(name = "messageType") @NotNull @Min(1) @Max(4) Short messageType)
    {
        userMessageService.checkAllMessages(loginState.getLoginUserId(token), messageType);
        return ResponseVO.success(null);
    }

    @Operation(summary = "将一条消息标记为已读")
    @PostMapping("/check/{messageId}")
    @Authorized
    public ResponseVO <Object> checkMessage(@RequestHeader(name = "token") @NotNull String token,
                                            @PathVariable(name = "messageId") @NotNull @Positive Long messageId)
    {
        userMessageService.checkMessage(loginState.getLoginUserId(token), messageId);
        return ResponseVO.success(null);
    }

    @Operation(summary = "获取单个分类的消息数量")
    @GetMapping("/{messageType}")
    @Authorized
    public ResponseVO <Integer> getMessageCount(@RequestHeader(name = "token") @NotNull String token,
                                                @PathVariable(name = "messageType") @NotNull @Min(1) @Max(4) Short messageType)
    {
        int count = userMessageService.getMessageCount(loginState.getLoginUserId(token), messageType);
        return ResponseVO.success(count);
    }

    @Operation(summary = "分页获取单个分类的消息")
    @GetMapping("/{messageType}/{pageNo}")
    @Authorized
    public ResponseVO <List <UserMessageDTO>> getMessages(@RequestHeader(name = "token") @NotNull String token,
                                                          @PathVariable(name = "messageType") @NotNull @Min(1) @Max(4)
                                                          Short messageType,
                                                          @PathVariable(name = "pageNo") @NotNull @Min(1) Integer pageNo)
    {
        List <UserMessageDTO> messages = userMessageService.getMessages(loginState.getLoginUserId(token), messageType, pageNo);
        return ResponseVO.success(messages);
    }

    @Operation(summary = "删除一条消息")
    @DeleteMapping("/{messageId}")
    @Authorized
    public ResponseVO <Object> deleteMessage(@RequestHeader(name = "token") @NotNull String token,
                                             @PathVariable(name = "messageId") @NotNull @Positive Long messageId)
    {
        userMessageService.deleteMessage(loginState.getLoginUserId(token), messageId);
        return ResponseVO.success(null);
    }
}
