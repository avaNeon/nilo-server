package com.neon.nilocommon.entity.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 媒体归属权批量校验 / 标记已使用请求
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MediaOwnershipBatchDTO
{
    @NotEmpty
    private List <String> objectKeys;

    @NotNull
    private Long ownerId;

    /**
     * 校验时使用的 used 状态（如 0 = 未使用）
     */
    private Integer used;

    /**
     * 标记已使用时的使用时间
     */
    private LocalDateTime usedTime;
}
