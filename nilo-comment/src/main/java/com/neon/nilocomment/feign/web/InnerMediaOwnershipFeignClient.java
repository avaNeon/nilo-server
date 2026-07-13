package com.neon.nilocomment.feign.web;

import com.neon.nilocommon.entity.dto.MediaOwnershipBatchDTO;
import com.neon.nilocommon.entity.vo.ResponseVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(value = "nilo-web", contextId = "innerMediaOwnershipFeignClient", path = "/inner/media/ownership")
public interface InnerMediaOwnershipFeignClient
{
    @PostMapping("/validate")
    ResponseVO <Integer> validate(@RequestBody MediaOwnershipBatchDTO request);

    @PostMapping("/mark")
    ResponseVO <Integer> markAsUsed(@RequestBody MediaOwnershipBatchDTO request);
}
