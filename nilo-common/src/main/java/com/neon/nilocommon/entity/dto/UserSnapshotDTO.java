package com.neon.nilocommon.entity.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户快照（跨服务精简字段，供评论等场景使用）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserSnapshotDTO
{
    @NotNull
    private Long userId;

    private String nickName;

    private String avatar;
}
