package com.neon.niloweb.controller.inner;

import com.neon.nilocommon.entity.enums.videoSearch.OrderType;
import com.neon.nilocommon.entity.vo.ResponseVO;
import com.neon.nilocommon.entity.vo.videoInfoDoc.VideoSearchResultVO;
import com.neon.niloweb.service.VideoSearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "内部-视频搜索接口", description = "仅供服务间调用")
@Validated
@RequiredArgsConstructor
@RequestMapping("/inner/video-search")
@RestController
public class InnerVideoSearchController
{
    private final VideoSearchService videoSearchService;

    /**
     * 关键词搜索视频，供 AI 等内部服务取检索上下文
     */
    @Operation(summary = "关键词搜索视频", description = "供 AI 服务检索上下文，不计入热搜、不高亮")
    @GetMapping
    public ResponseVO <VideoSearchResultVO> searchVideo(
            @RequestParam(name = "keyword") @NotEmpty @Size(max = 100) String keyword,
            @RequestParam(name = "pageSize", defaultValue = "5") @Min(1) @Max(20) Integer pageSize)
    {
        // 综合排序；不高亮、不计入热搜
        return ResponseVO.success(videoSearchService.searchVideo(OrderType.COMPREHENSIVE.getValue(),
                                                                 1,
                                                                 pageSize,
                                                                 false,
                                                                 keyword,
                                                                 false));
    }
}
