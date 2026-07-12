package com.neon.nilomqconsumer.feign.storage;

import com.neon.nilocommon.entity.vo.ResponseVO;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(value = "nilo-storage", contextId = "videoFileFeignClient", path = "/video")
public interface VideoFileFeignClient
{
    @DeleteMapping(path = "/delete/recursively")
    ResponseVO <Void> deleteRecursively(@RequestBody @NotEmpty String baseKey);

    @DeleteMapping(path = "/delete/object")
    ResponseVO <Void> deleteObject(@RequestBody @NotEmpty String key);
}
