package com.neon.niloweb.controller.inner;

import com.neon.nilocommon.entity.dto.MediaOwnershipBatchDTO;
import com.neon.nilocommon.entity.enums.ResponseCode;
import com.neon.nilocommon.entity.vo.ResponseVO;
import com.neon.nilocommon.exception.BusinessException;
import com.neon.niloweb.service.FileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "内部-媒体归属权接口", description = "仅供服务间调用")
@Validated
@RequiredArgsConstructor
@RequestMapping("/inner/media/ownership")
@RestController
public class InnerMediaOwnershipController
{
    private final FileService fileService;

    @Operation(summary = "批量校验媒体归属权")
    @PostMapping("/validate")
    public ResponseVO <Integer> validate(@RequestBody @Valid MediaOwnershipBatchDTO mediaOwnershipBatchDTO)
    {
        if (mediaOwnershipBatchDTO.getUsed() == null)
        {
            throw new BusinessException(ResponseCode.WRONG_ARGUMENTS);
        }
        int count = fileService.countExistByKeysAndOwnerIdAndUsed(mediaOwnershipBatchDTO.getObjectKeys(),
                                                                  mediaOwnershipBatchDTO.getOwnerId(),
                                                                  mediaOwnershipBatchDTO.getUsed());
        return ResponseVO.success(count);
    }

    @Operation(summary = "批量标记媒体为已使用")
    @PostMapping("/mark")
    public ResponseVO <Integer> markAsUsed(@RequestBody @Valid MediaOwnershipBatchDTO request)
    {
        if (request.getUsedTime() == null)
        {
            throw new BusinessException(ResponseCode.WRONG_ARGUMENTS);
        }
        int affected = fileService.markMediaAsUsedBatch(request.getObjectKeys(), request.getOwnerId(), request.getUsedTime());
        return ResponseVO.success(affected);
    }
}
