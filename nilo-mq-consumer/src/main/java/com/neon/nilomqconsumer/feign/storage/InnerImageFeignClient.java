package com.neon.nilomqconsumer.feign.storage;

import com.neon.nilocommon.entity.vo.ResponseVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@FeignClient(value = "nilo-storage", contextId = "imageFeignClient", path = "/inner/image")
public interface InnerImageFeignClient
{
    /**
     * 删除所有状态的文件
     */
    @DeleteMapping(path = "/delete/batch")
    ResponseVO <Void> batchDelete(@RequestBody List <String> baseKeys);
}
