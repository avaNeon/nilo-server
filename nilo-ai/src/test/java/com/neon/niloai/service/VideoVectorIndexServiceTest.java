package com.neon.niloai.service;

import com.neon.nilocommon.entity.dto.VideoEmbedSourceDTO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 简介切块的纯函数测试，不连任何外部服务
 */
class VideoVectorIndexServiceTest
{
    @Test
    void 简介里的链接去掉换行还原()
    {
        String cleaned = VideoVectorIndexService.cleanIntroduction("看这里 https://example.com/a?b=1 就够了\\n第二行");

        assertFalse(cleaned.contains("http"));
        assertTrue(cleaned.contains("\n第二行"));
    }

    @Test
    void 主文档只放简介开头()
    {
        String introduction = "简".repeat(1000);

        String text = VideoVectorIndexService.buildEmbedText(new VideoEmbedSourceDTO(1L, "标题", "标签", introduction));

        // 标题、标签、简介三行的前缀加上 300 个字符的简介
        assertTrue(text.contains("标题：标题"));
        assertEquals(300, text.substring(text.indexOf("简介：") + 3).length());
    }

    @Test
    void 超出开头的简介按段切块()
    {
        // 2000 字符是数据库给简介的上限
        List <String> chunks = VideoVectorIndexService.restChunks("字".repeat(2000));

        assertEquals(6, chunks.size());
        assertEquals(300, chunks.get(0).length());
        assertEquals(200, chunks.get(chunks.size() - 1).length());
    }

    @Test
    void 简介不长就没有额外的块()
    {
        assertTrue(VideoVectorIndexService.restChunks("短简介").isEmpty());
    }

    @Test
    void 优先切在换行上()
    {
        String introduction = "头".repeat(300) + "第一段" + "\n" + "第二段" + "内".repeat(400);

        List <String> chunks = VideoVectorIndexService.restChunks(introduction);

        assertTrue(chunks.get(0).endsWith("第一段"));
        assertTrue(chunks.get(1).startsWith("第二段"));
    }
}
