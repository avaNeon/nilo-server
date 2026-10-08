package com.neon.nilocanalclient.canal;

import com.alibaba.otter.canal.protocol.CanalEntry;
import com.neon.nilocommon.entity.dto.UserSnapshotDTO;
import com.neon.nilocommon.entity.dto.VideoSnapshotDTO;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Canal 行数据 → 评论库副本表需要的 {@link VideoSnapshotDTO}、{@link UserSnapshotDTO}
 */
@Component
public class CommentReplicaCanalTranslator
{
    /**
     * 将 video_info 的一行转换为视频快照
     *
     * @param columns 一行的所有列数据
     * @return 视频快照；video_id 为空时 videoId 字段为 null，由调用方跳过
     */
    public VideoSnapshotDTO toVideo(List <CanalEntry.Column> columns)
    {
        Map <String, CanalEntry.Column> columnMap = toColumnMap(columns);

        return new VideoSnapshotDTO(parseLong(textOf(columnMap.get("video_id"))),
                                    parseLong(textOf(columnMap.get("user_id"))),
                                    textOf(columnMap.get("video_name")),
                                    textOf(columnMap.get("video_cover")),
                                    textOf(columnMap.get("interaction")));
    }

    /**
     * 将 user_info 的一行转换为用户快照
     *
     * @param columns 一行的所有列数据
     * @return 用户快照；user_id 为空时 userId 字段为 null，由调用方跳过
     */
    public UserSnapshotDTO toUser(List <CanalEntry.Column> columns)
    {
        Map <String, CanalEntry.Column> columnMap = toColumnMap(columns);

        return new UserSnapshotDTO(parseLong(textOf(columnMap.get("user_id"))),
                                   textOf(columnMap.get("nick_name")),
                                   textOf(columnMap.get("avatar")));
    }

    /**
     * 将一行所有字段数据从List转化为Map格式
     */
    private Map <String, CanalEntry.Column> toColumnMap(List <CanalEntry.Column> columns)
    {
        return columns.stream().collect(Collectors.toMap(CanalEntry.Column::getName, column -> column, (a, b) -> b));
    }

    private String textOf(CanalEntry.Column column)
    {
        // 取列的文本值。数据库里是 NULL 的列返回 null，而不是 canal 默认给的空字符串，这样副本表和主库保持一致
        if (column == null || column.getIsNull())
        {
            return null;
        }
        return column.getValue();
    }

    private Long parseLong(String value)
    {
        if (!StringUtils.hasText(value))
        {
            return null;
        }
        return Long.valueOf(value);
    }
}
