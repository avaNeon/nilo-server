package com.neon.nilocanalclient.service;

import com.alibaba.otter.canal.protocol.CanalEntry;
import com.neon.nilocanalclient.canal.CommentReplicaCanalTranslator;
import com.neon.nilocanalclient.config.CanalProperties;
import com.neon.nilocanalclient.repository.rabbitmq.CommentReplicaMqRepository;
import com.neon.nilocommon.entity.constants.NiloTable;
import com.neon.nilocommon.entity.dto.UserSnapshotDTO;
import com.neon.nilocommon.entity.dto.VideoSnapshotDTO;
import com.neon.nilocommon.entity.dto.mq.UserReplicaSyncDTO;
import com.neon.nilocommon.entity.dto.mq.VideoReplicaSyncDTO;
import com.neon.nilocommon.entity.enums.comment.ReplicaSyncType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * comment 微服务同步业务代码<hr/>
 * <p>把 video_info、user_info 的新增、更新和删除同步到评论库的副本表</p>
 * <p>评论服务有自己的库，评论接口靠副本表判断视频是否存在。新视频、新用户没有副本行，评论就用不了；
 * 已删除的视频留着副本行，就还能被评论，这些评论不会随视频归档，成了脏数据。
 * 所以这里和 ES、AI 不同：发消息失败要抛异常，让整批 Canal 数据回滚后重新处理，而不是只记日志。重复处理没有副作用。</p>
 * <p>删除也在这里同步，不依赖各个删除入口自己记得通知评论服务：以后新增的删除视频、注销用户的入口同样会被覆盖。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CommentReplicaSyncCanalService
{
    /**
     * 只有这几列变了才值得同步视频副本。播放量、点赞数这些计数列一直在变，跟着同步纯属浪费
     */
    private static final Set <String> VIDEO_REPLICA_COLUMNS = Set.of("user_id", "video_name", "video_cover", "interaction");

    /**
     * 只有这几列变了才值得同步用户副本。金币、最后登录时间这些列也会常变
     */
    private static final Set <String> USER_REPLICA_COLUMNS = Set.of("nick_name", "avatar");

    private final CanalProperties canalProperties;

    private final CommentReplicaCanalTranslator commentReplicaCanalTranslator;

    private final CommentReplicaMqRepository commentReplicaMqRepository;

    public void handleEntries(List <CanalEntry.Entry> entries)
    {
        if (!canalProperties.isCommentReplicaEnabled())
        {
            return;
        }

        // 同一 Canal 批次内按主键折叠，只保留最后一次操作（保持事件顺序）：先新增后删除就只发删除，先删除后新增就只发新增
        Map <Long, VideoReplicaSyncDTO> videoById = new LinkedHashMap <>();
        Map <Long, UserReplicaSyncDTO> userById = new LinkedHashMap <>();

        for (CanalEntry.Entry entry : entries)
        {
            if (entry.getEntryType() != CanalEntry.EntryType.ROWDATA)
            {
                continue;
            }

            // 先检查一下是不是自己负责的表
            String tableName = entry.getHeader().getTableName();
            boolean isVideoTable = NiloTable.VIDEO_INFO.equalsIgnoreCase(tableName);
            if (!isVideoTable && !NiloTable.USER_INFO.equalsIgnoreCase(tableName))
            {
                continue;
            }

            // 获取一行数据库变更
            CanalEntry.RowChange rowChange;
            try
            {
                rowChange = CanalEntry.RowChange.parseFrom(entry.getStoreValue());
            }
            catch (Exception e)
            {
                throw new IllegalStateException("解析 Canal RowChange 失败, table=" + tableName, e);
            }

            // 新增、更新对应「新增或覆盖」，删除对应删除，忽略其它事件（如建表、改表结构）
            CanalEntry.EventType eventType = rowChange.getEventType();
            ReplicaSyncType syncType = switch (eventType)
            {
                case INSERT, UPDATE -> ReplicaSyncType.UPSERT;
                case DELETE -> ReplicaSyncType.DELETE;
                default -> null;
            };
            if (syncType == null)
            {
                continue;
            }

            for (CanalEntry.RowData rowData : rowChange.getRowDatasList())
            {
                try
                {
                    // 删除事件只有删除前的列，新增、更新取变更后的列
                    List <CanalEntry.Column> columns = syncType == ReplicaSyncType.DELETE ? rowData.getBeforeColumnsList() : rowData.getAfterColumnsList();
                    if (eventType == CanalEntry.EventType.UPDATE && !isAnyUpdated(columns,
                                                                                isVideoTable ? VIDEO_REPLICA_COLUMNS : USER_REPLICA_COLUMNS))
                    {
                        continue;
                    }

                    if (isVideoTable)
                    {
                        collectVideo(videoById, columns, eventType, syncType);
                    }
                    else
                    {
                        collectUser(userById, columns, eventType, syncType);
                    }
                }
                catch (Exception e)
                {
                    // 单行转换失败不影响同批其它行，也不阻塞后续 Canal 批次
                    log.warn("跳过无法转换的 {} {} 行事件", tableName, eventType, e);
                }
            }
        }

        // 发消息失败直接抛出，由 CanalClientRunner 回滚这一批
        for (VideoReplicaSyncDTO video : videoById.values())
        {
            commentReplicaMqRepository.sendVideoReplica(video);
        }
        for (UserReplicaSyncDTO user : userById.values())
        {
            commentReplicaMqRepository.sendUserReplica(user);
        }

        if (!videoById.isEmpty() || !userById.isEmpty())
        {
            log.info("已通知评论服务同步副本, video数量={}, user数量={}", videoById.size(), userById.size());
        }
    }

    /**
     * 收集要同步的视频
     */
    private void collectVideo(Map <Long, VideoReplicaSyncDTO> videoById,
                              List <CanalEntry.Column> columns,
                              CanalEntry.EventType eventType,
                              ReplicaSyncType syncType)
    {
        VideoSnapshotDTO video = commentReplicaCanalTranslator.toVideo(columns);

        // 跳过无 video_id 的数据
        if (video.getVideoId() == null)
        {
            log.warn("跳过无 video_id 的 {} 事件", eventType);
            return;
        }

        videoById.put(video.getVideoId(), new VideoReplicaSyncDTO(syncType, video));
    }

    /**
     * 收集要同步的用户
     */
    private void collectUser(Map <Long, UserReplicaSyncDTO> userById,
                             List <CanalEntry.Column> columns,
                             CanalEntry.EventType eventType,
                             ReplicaSyncType syncType)
    {
        UserSnapshotDTO user = commentReplicaCanalTranslator.toUser(columns);

        // 跳过无 user_id 的数据
        if (user.getUserId() == null)
        {
            log.warn("跳过无 user_id 的 {} 事件", eventType);
            return;
        }

        userById.put(user.getUserId(), new UserReplicaSyncDTO(syncType, user));
    }

    /**
     * 这一行更新里，是否有 columnNames 中的列真正发生了变化
     */
    private boolean isAnyUpdated(List <CanalEntry.Column> columns, Set <String> columnNames)
    {
        return columns.stream().anyMatch(column -> column.getUpdated() && columnNames.contains(column.getName()));
    }
}
