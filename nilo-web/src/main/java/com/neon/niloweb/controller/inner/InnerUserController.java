package com.neon.niloweb.controller.inner;

import com.neon.nilocommon.entity.dto.UserInfoDTO;
import com.neon.nilocommon.entity.vo.ResponseVO;
import com.neon.niloweb.service.UserHomeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "内部-用户接口", description = "仅供服务间调用")
@Validated
@RequiredArgsConstructor
@RequestMapping("/inner/user")
@RestController
public class InnerUserController
{
    private final UserHomeService userHomeService;

    @Operation(summary = "获取用户资料快照")
    @GetMapping("/{userId}")
    public ResponseVO <UserInfoDTO> getUserInfo(@PathVariable(name = "userId") @NotNull Long userId)
    {
        return ResponseVO.success(userHomeService.getUserInfo(userId));
    }
}
