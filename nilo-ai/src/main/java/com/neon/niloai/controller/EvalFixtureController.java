package com.neon.niloai.controller;

import com.neon.niloai.entity.vo.EvalFixtureOpVO;
import com.neon.niloai.entity.vo.RetrievalEvalReportVO;
import com.neon.niloai.eval.EvalFixtureCatalog;
import com.neon.niloai.eval.EvalFixtureCatalog.EvalCase;
import com.neon.niloai.service.EvalFixtureService;
import com.neon.nilocommon.entity.vo.ResponseVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "检索评测接口")
@Validated
@RequiredArgsConstructor
@RequestMapping("/ai/eval")
@RestController
public class EvalFixtureController
{
    private final EvalFixtureService evalFixtureService;

    /**
     * 把固定 videoId 的假视频写入向量索引和 video_info_doc
     */
    @Operation(summary = "灌入检索评测样例")
    @PostMapping("/fixtures")
    public ResponseVO <EvalFixtureOpVO> seed()
    {
        return ResponseVO.success(evalFixtureService.seed());
    }

    /**
     * 按固定 videoId 删除评测样例，不影响其它视频
     */
    @Operation(summary = "删除检索评测样例")
    @DeleteMapping("/fixtures")
    public ResponseVO <EvalFixtureOpVO> delete()
    {
        return ResponseVO.success(evalFixtureService.delete());
    }

    @Operation(summary = "查看评测考题")
    @GetMapping("/cases")
    public ResponseVO <List <EvalCase>> cases()
    {
        return ResponseVO.success(EvalFixtureCatalog.cases());
    }

    /**
     * 用当前向量召回跑 Hit@5，不调用 Chat，作为 v1 基线
     */
    @Operation(summary = "评测当前向量召回 Hit@5")
    @PostMapping("/retrieval")
    public ResponseVO <RetrievalEvalReportVO> evaluateRetrieval()
    {
        return ResponseVO.success(evalFixtureService.evaluateRetrieval());
    }
}
