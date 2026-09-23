package com.neon.nilomqconsumer.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.neon.nilocommon.entity.constants.Constants;
import com.neon.nilocommon.entity.constants.MinioBucket;
import com.neon.nilocommon.entity.constants.MinioKey;
import com.neon.nilocommon.entity.dto.subtitle.SubtitleChapterDTO;
import com.neon.nilocommon.entity.dto.subtitle.SubtitleSummaryDTO;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.errors.ErrorResponseException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 给已发布的老视频补 summary.json<hr/>
 *
 * <p>总结现在由转码流程生成，d32b4a3 之前转码的视频没有。这个测试读 public/ 下现成的字幕，
 * 补出总结和章节再传回同一目录，不重新做语音识别。
 *
 * <p>会调收费接口，打了 manual 标签，默认跳过；加 -Dbackfill.force=true 可以覆盖已有的总结。
 */
@Tag("manual")
@SpringBootTest(properties = "spring.rabbitmq.listener.simple.auto-startup=false")
class SummaryBackfillTest
{
    private static final Pattern SRT_TIME = Pattern.compile("(\\d{2}):(\\d{2}):(\\d{2}),(\\d{3}) -->");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MinioClient minioClient;

    @Autowired
    private SubtitleLlmService subtitleLlmService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void backfill()
    {
        boolean force = Boolean.getBoolean("backfill.force");
        List <Map <String, Object>> files = jdbcTemplate.queryForList(
                "select video_id, file_index, file_path from video_info_file order by video_id, file_index");
        int done = 0;
        int skipped = 0;
        List <String> failed = new ArrayList <>();
        for (Map <String, Object> file : files)
        {
            String filePath = String.valueOf(file.get("file_path"));
            String base = MinioKey.PUBLIC_PREFIX + filePath + "/";
            try
            {
                if (!force && exists(base + Constants.SUMMARY_NAME))
                {
                    skipped++;
                    continue;
                }
                String srt = read(base + Constants.SUBTITLE_NAME);
                if (srt == null)
                {
                    skipped++;
                    continue;
                }
                SubtitleSummaryDTO summary = subtitleLlmService.summarize(toTimedLines(srt));
                summary.setChapters(clean(summary.getChapters()));
                byte[] json = objectMapper.writeValueAsBytes(summary);
                minioClient.putObject(PutObjectArgs.builder()
                                                   .bucket(MinioBucket.MINIO_VIDEO_BUCKET)
                                                   .object(base + Constants.SUMMARY_NAME)
                                                   .contentType("application/json; charset=utf-8")
                                                   .stream(new ByteArrayInputStream(json), (long) json.length, -1L)
                                                   .build());
                done++;
                System.out.printf("补齐总结 videoId=%s P%s chapters=%d%n",
                                  file.get("video_id"),
                                  file.get("file_index"),
                                  summary.getChapters().size());
            }
            catch (Exception e)
            {
                failed.add(filePath + " " + e);
            }
        }
        System.out.printf("完成 %d，跳过 %d（没字幕或已有总结），失败 %d%n", done, skipped, failed.size());
        failed.forEach(System.out::println);
    }

    /**
     * SRT 转成「[秒数] 台词」，和转码时喂给模型的格式一致
     */
    private String toTimedLines(String srt)
    {
        StringBuilder lines = new StringBuilder();
        for (String block : srt.replace("\r\n", "\n").split("\n\\s*\n"))
        {
            String[] blockLines = block.trim().split("\n");
            for (int i = 0 ; i < blockLines.length ; i++)
            {
                Matcher matcher = SRT_TIME.matcher(blockLines[i]);
                if (!matcher.find())
                {
                    continue;
                }
                int sec = Integer.parseInt(matcher.group(1)) * 3600 + Integer.parseInt(matcher.group(2)) * 60
                          + Integer.parseInt(matcher.group(3));
                if (!lines.isEmpty())
                {
                    lines.append('\n');
                }
                lines.append('[').append(sec).append("] ").append(String.join(" ", List.of(blockLines).subList(i + 1, blockLines.length)));
                break;
            }
        }
        return lines.toString();
    }

    /**
     * 章节去重排序，丢掉没标题的。补跑时时间已经取自字幕行首，不再额外吸附
     */
    private List <SubtitleChapterDTO> clean(List <SubtitleChapterDTO> chapters)
    {
        List <SubtitleChapterDTO> cleaned = new ArrayList <>();
        if (chapters == null)
        {
            return cleaned;
        }
        Set <Integer> used = new HashSet <>();
        for (SubtitleChapterDTO chapter : chapters)
        {
            if (chapter != null && chapter.getStartSec() != null && chapter.getStartSec() >= 0 && chapter.getTitle() != null
                && !chapter.getTitle().isBlank() && used.add(chapter.getStartSec()))
            {
                cleaned.add(new SubtitleChapterDTO(chapter.getStartSec(), chapter.getTitle().trim()));
            }
        }
        cleaned.sort(Comparator.comparing(SubtitleChapterDTO::getStartSec));
        return cleaned;
    }

    private boolean exists(String key) throws Exception
    {
        try
        {
            minioClient.statObject(StatObjectArgs.builder().bucket(MinioBucket.MINIO_VIDEO_BUCKET).object(key).build());
            return true;
        }
        catch (ErrorResponseException e)
        {
            if (e.errorResponse().code().startsWith("NoSuch"))
            {
                return false;
            }
            throw e;
        }
    }

    /**
     * @return 对象内容；不存在时返回 null
     */
    private String read(String key) throws Exception
    {
        try (InputStream in = minioClient.getObject(GetObjectArgs.builder()
                                                                 .bucket(MinioBucket.MINIO_VIDEO_BUCKET)
                                                                 .object(key)
                                                                 .build()))
        {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        catch (ErrorResponseException e)
        {
            if (e.errorResponse().code().startsWith("NoSuch"))
            {
                return null;
            }
            throw e;
        }
    }
}
