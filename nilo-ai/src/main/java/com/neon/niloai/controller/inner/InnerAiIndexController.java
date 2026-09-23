package com.neon.niloai.controller.inner;

import com.neon.niloai.service.SubtitleChunkService;
import com.neon.niloai.service.VideoVectorIndexService;
import com.neon.nilocommon.entity.vo.ResponseVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 向量索引的重建入口<hr/>
 * <p>正常情况下不用手动调：canal 监听到视频变化会发消息，由 nilo-mq-consumer 转发到这里。
 * 带 videoId 的几个就是给它用的。</p>
 * <p>不带 videoId 的两个是全量重建，留给换 embedding 模型、死信堆积、canal 漏事件这些场合人工补救。</p>
 */
@Tag(name = "内部-AI 索引接口", description = "仅供服务间调用")
@Validated
@RequiredArgsConstructor
@RequestMapping("/inner/index")
@RestController
public class InnerAiIndexController
{
    private final VideoVectorIndexService videoVectorIndexService;

    private final SubtitleChunkService subtitleChunkService;

    /**
     * 重建单个视频的向量，先按 videoId 删旧的再写
     *
     * @return 写入的文档数
     */
    @Operation(summary = "重建单个视频的向量")
    @PostMapping("/video/{videoId}")
    public ResponseVO <Integer> indexVideo(@PathVariable(name = "videoId") @NotNull Long videoId)
    {
        return ResponseVO.success(videoVectorIndexService.indexVideo(videoId));
    }

    @Operation(summary = "删除单个视频的向量")
    @DeleteMapping("/video/{videoId}")
    public ResponseVO <Void> deleteVideo(@PathVariable(name = "videoId") @NotNull Long videoId)
    {
        videoVectorIndexService.deleteVideo(videoId);
        return ResponseVO.success();
    }

    /**
     * 重建单个视频的字幕块，先删光旧块再照库里现有的分P重灌。分P没了就是只删不写
     *
     * @return 写入的块数
     */
    @Operation(summary = "重建单个视频的字幕块")
    @PostMapping("/subtitle/{videoId}")
    public ResponseVO <Integer> indexSubtitle(@PathVariable(name = "videoId") @NotNull Long videoId)
    {
        return ResponseVO.success(subtitleChunkService.indexVideo(videoId));
    }

    /**
     * 从 MySQL 分页读取标题、标签、简介并写入向量索引
     *
     * @return 写入的文档数
     */
    @Operation(summary = "全量灌入视频向量")
    @PostMapping("/video")
    public ResponseVO <Integer> fullIndexVideo()
    {
        return ResponseVO.success(videoVectorIndexService.fullIndex());
    }

    /**
     * 清空字幕块索引后重读所有已发布视频的字幕，按 30 秒一块切开写入
     *
     * @return 写入的块数
     */
    @Operation(summary = "全量灌入字幕块向量")
    @PostMapping("/subtitle")
    public ResponseVO <Integer> fullIndexSubtitle()
    {
        return ResponseVO.success(subtitleChunkService.fullIndex());
    }
}
