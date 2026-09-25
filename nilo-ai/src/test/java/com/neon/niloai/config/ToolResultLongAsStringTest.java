package com.neon.niloai.config;

import com.neon.niloai.entity.vo.VideoHitVO;
import com.neon.nilocommon.entity.po.document.VideoInfoDoc;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.ai.util.json.JsonParser;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 工具 JSON 里超过 JS 安全整数的 Long 必须是字符串
 */
class ToolResultLongAsStringTest
{
    private static final long VIDEO_ID = 1567890123456789012L;

    @BeforeAll
    static void stringifyLong() throws Exception
    {
        new LlmConfig().toolResultLongAsString().afterPropertiesSet();
    }

    @Test
    void 检索结果的videoId写成字符串()
    {
        String json = JsonParser.toJson(List.of(new VideoHitVO(VIDEO_ID, "多线程", "原文")));

        assertTrue(json.contains("\"videoId\":\"1567890123456789012\""));
    }

    @Test
    void 详情里的videoId和userId写成字符串()
    {
        VideoInfoDoc video = new VideoInfoDoc();
        video.setVideoId(VIDEO_ID);
        video.setUserId(VIDEO_ID);
        video.setPlayCount(12);
        video.setLastUpdateTime(LocalDateTime.of(2026, 9, 24, 11, 0, 0));

        String json = JsonParser.toJson(video);

        assertTrue(json.contains("\"videoId\":\"1567890123456789012\""));
        assertTrue(json.contains("\"userId\":\"1567890123456789012\""));
        assertTrue(json.contains("\"playCount\":12"));
    }
}
