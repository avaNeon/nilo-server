package com.neon.niloadmin.feign.storage;

import com.neon.nilocommon.entity.vo.ResponseVO;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@FeignClient(value = "nilo-storage", contextId = "imageFeignClient", path = "/image")
public interface ImageFeignClient
{
    @PutMapping(path = "/move/batch")
    ResponseVO <Void> batchMove(@RequestBody @NotEmpty Map <String, String> keyMap);

    @GetMapping(path = "/download")
    ResponseVO <String> downloadImage(@RequestParam(name = "key") String key,
                                      @RequestParam(name = "expireSeconds") Integer expireSeconds);

    @GetMapping(path = "/key/probe")
    ResponseVO <String> probeImageObjectKey(@RequestParam(name = "baseKey") String baseKey);
}
