package com.neon.nilomqconsumer.feign.storage;

import com.neon.nilocommon.entity.vo.ResponseVO;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@FeignClient(value = "nilo-storage", contextId = "imageFeignClient", path = "/image")
public interface ImageFeignClient
{
    /**
     * 删除所有状态的文件
     */
    @DeleteMapping(path = "/delete/batch")
    ResponseVO <Void> batchDelete(@RequestBody List <String> baseKeys);
}
