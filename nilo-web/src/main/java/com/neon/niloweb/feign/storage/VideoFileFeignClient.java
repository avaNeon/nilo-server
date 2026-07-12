package com.neon.niloweb.feign.storage;

import com.neon.nilocommon.entity.vo.ResponseVO;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@FeignClient(value = "nilo-storage", contextId = "videoFileFeignClient", path = "/video")
public interface VideoFileFeignClient
{
    @GetMapping(path = "/upload")
    ResponseVO <Map <String, String>> upload(@RequestParam(name = "key") String key,
                                             @RequestParam(name = "expireSeconds") Long expireSeconds,
                                             @RequestParam(name = "maxBytes") Long maxBytes);

    @PutMapping(path = "/move")
    ResponseVO <Void> move(@RequestParam(name = "srcKey") String srcKey, @RequestParam(name = "destKey") String destKey);

    @PutMapping(path = "/move/batch")
    ResponseVO <Void> batchMove(@RequestBody Map <String, String> keyMap);

    @PutMapping(path = "/move/directory/batch")
    ResponseVO <Void> batchMoveDirectory(@RequestBody Map <String, String> directoryMap);

    @GetMapping(path = "/size")
    ResponseVO <Long> getVideoFileSize(@RequestParam(name = "key") String key);

    @GetMapping(path = "/size/probe")
    ResponseVO <Long> probeVideoFileSize(@RequestParam(name = "baseKey") @NotEmpty String baseKey);

    @GetMapping(path = "/download")
    ResponseVO <String> downloadVideo(@RequestParam(name = "key") String key,
                                      @RequestParam(name = "expireSeconds") Integer expireSeconds);

    /**
     * 递归删除所有状态的文件（含转码产物）
     */
    @DeleteMapping(path = "/delete/recursively/batch")
    ResponseVO <Void> batchDeleteRecursively(@RequestBody List <String> baseKeys);
}
