package com.neon.niloadmin.feign.storage;

import com.neon.nilocommon.entity.vo.ResponseVO;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@FeignClient(value = "nilo-storage", contextId = "videoFileFeignClient", path = "/inner/video")
public interface InnerVideoFileFeignClient
{
    @PutMapping(path = "/move/batch")
    ResponseVO <Void> batchMove(@RequestBody @NotEmpty Map <String, String> keyMap);

    @PutMapping(path = "/move/directory/batch")
    ResponseVO <Void> batchMoveDirectory(@RequestBody @NotEmpty Map <String, String> directoryMap);

    @GetMapping(path = "/download")
    ResponseVO <String> downloadVideo(@RequestParam(name = "key") String key,
                                      @RequestParam(name = "expireSeconds") Integer expireSeconds);
}
