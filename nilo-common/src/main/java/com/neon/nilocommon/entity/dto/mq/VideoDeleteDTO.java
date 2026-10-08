package com.neon.nilocommon.entity.dto.mq;

import com.neon.nilocommon.entity.enums.videoInfoArchive.DeleterType;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * 删除视频的任务<hr/>
 * 用户在创作中心删除、管理员在后台删除都发这条消息，由 nilo-web 统一处理。
 * 是否已发布不在这里标明，处理时按当时的数据重新判断
 */
@Getter
@Setter
@ToString
@AllArgsConstructor
@NoArgsConstructor
public class VideoDeleteDTO
{
    /**
     * 视频发布者ID
     */
    @NotNull
    private Long userId;

    @NotNull
    private Long videoId;

    /**
     * 谁删的，写进归档表
     */
    @NotNull
    private DeleterType deleterType;

    /**
     * 删除原因，写进归档表
     */
    private String detail;
}
