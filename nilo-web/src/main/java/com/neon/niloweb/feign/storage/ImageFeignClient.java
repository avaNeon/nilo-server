package com.neon.niloweb.feign.storage;

import com.neon.nilocommon.entity.vo.ResponseVO;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@FeignClient(value = "nilo-storage", contextId = "imageFeignClient", path = "/image")
public interface ImageFeignClient
{
    @PutMapping(path = "/move")
    ResponseVO <Void> move(@RequestParam(name = "srcKey") String srcKey, @RequestParam(name = "destKey") String destKey);

    @PutMapping(path = "/move/batch")
    ResponseVO <Void> batchMove(@RequestBody @NotEmpty Map <String, String> keyMap);

    @GetMapping(path = "/size")
    ResponseVO <Long> getImageSize(@RequestParam(name = "key") String key);

    @GetMapping(path = "/download")
    ResponseVO <String> downloadImage(@RequestParam(name = "key") String key,
                                      @RequestParam(name = "expireSeconds") Integer expireSeconds);

    @DeleteMapping(path = "/delete")
    ResponseVO <Void> delete(@RequestParam(name = "key") String key);

    /**
     * 删除所有状态的文件
     */
    @DeleteMapping(path = "/delete/batch")
    ResponseVO <Void> batchDelete(@RequestBody List <String> baseKeys);
}
