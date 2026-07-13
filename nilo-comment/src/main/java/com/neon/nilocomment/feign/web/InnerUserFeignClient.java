package com.neon.nilocomment.feign.web;

import com.neon.nilocommon.entity.dto.UserInfoDTO;
import com.neon.nilocommon.entity.vo.ResponseVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(value = "nilo-web", contextId = "innerUserFeignClient", path = "/inner/user")
public interface InnerUserFeignClient
{
    @GetMapping("/{userId}")
    ResponseVO <UserInfoDTO> getUserInfo(@PathVariable(name = "userId") Long userId);
}
