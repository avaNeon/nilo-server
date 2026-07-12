package com.neon.nilocanalclient.canal;

import com.alibaba.otter.canal.protocol.CanalEntry;
import com.neon.nilocommon.entity.constants.DatePattern;
import com.neon.nilocommon.entity.po.document.VideoInfoDoc;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Canal 行数据 → {@link VideoInfoDoc}
 */
@Component
public class VideoInfoDocCanalTranslator
{
    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern(DatePattern.DATETIME);

    /**
     * 将每行数据转换为 {@link VideoInfoDoc}
     *
     * @param columns 一行的所有列数据
     * @return 可插入ES的{@link VideoInfoDoc}对象
     */
    public VideoInfoDoc toDoc(List <CanalEntry.Column> columns)
    {
        // 解析字段
        Map <String, String> columnMap = toColumnMap(columns);

        // 装配列数据
        VideoInfoDoc doc = new VideoInfoDoc();
        doc.setVideoId(parseLong(columnMap.get("video_id")));
        doc.setVideoCover(emptyToNull(columnMap.get("video_cover")));
        doc.setVideoName(emptyToNull(columnMap.get("video_name")));
        doc.setDuration(parseInteger(columnMap.get("duration")));
        doc.setUserId(parseLong(columnMap.get("user_id")));
        doc.setLastUpdateTime(parseDateTime(columnMap.get("last_update_time")));
        doc.setCategoryId(parseInteger(columnMap.get("category_id")));
        doc.setTags(parseTags(columnMap.get("tags")));
        doc.setPlayCount(parseInteger(columnMap.get("play_count")));
        doc.setDanmakuCount(parseInteger(columnMap.get("danmaku_count")));
        doc.setCollectCount(parseInteger(columnMap.get("collect_count")));

        return doc;
    }

    /**
     * 从行的所有字段中提取 video_id 字段数据
     *
     * @param columns 一行的所有字段数据
     * @return 如果存在，返回 video_id 数据
     */
    public Long extractVideoId(List <CanalEntry.Column> columns)
    {
        return parseLong(toColumnMap(columns).get("video_id"));
    }

    /**
     * 将一行所有字段数据从List转化为Map格式
     *
     * @param columns 一行的所有字段数据
     * @return 转化后的Map数据
     */
    private Map <String, String> toColumnMap(List <CanalEntry.Column> columns)
    {
        return columns.stream().collect(Collectors.toMap(CanalEntry.Column::getName, CanalEntry.Column::getValue, (a, b) -> b));
    }

    private List <String> parseTags(String tags)
    {
        if (!StringUtils.hasText(tags))
        {
            return List.of();
        }
        return Arrays.stream(tags.split(",")).map(String::trim).filter(StringUtils::hasText).toList();
    }

    private LocalDateTime parseDateTime(String value)
    {
        if (!StringUtils.hasText(value))
        {
            return null;
        }
        // MySQL datetime 可能带小数秒，先截到秒
        String normalized = value.length() > 19 ? value.substring(0, 19) : value;
        return LocalDateTime.parse(normalized, DATETIME_FORMATTER);
    }

    private Long parseLong(String value)
    {
        if (!StringUtils.hasText(value))
        {
            return null;
        }
        return Long.valueOf(value);
    }

    private Integer parseInteger(String value)
    {
        if (!StringUtils.hasText(value))
        {
            return null;
        }
        return Integer.valueOf(value);
    }

    private String emptyToNull(String value)
    {
        return StringUtils.hasText(value) ? value : null;
    }
}
