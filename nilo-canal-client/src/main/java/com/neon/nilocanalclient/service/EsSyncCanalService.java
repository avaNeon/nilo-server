package com.neon.nilocanalclient.service;

import com.alibaba.otter.canal.protocol.CanalEntry;
import com.neon.nilocanalclient.canal.VideoInfoDocCanalTranslator;
import com.neon.nilocanalclient.entity.VideoInfoDocBulkOpt;
import com.neon.nilocanalclient.repository.elasticsearch.VideoInfoDocBulkRepository;
import com.neon.nilocanalclient.repository.rabbitmq.VideoIndexMqRepository;
import com.neon.nilocommon.entity.constants.NiloTable;
import com.neon.nilocommon.entity.enums.videoIndex.VideoIndexTaskType;
import com.neon.nilocommon.entity.po.document.VideoInfoDoc;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * ES 同步业务代码<hr/>
 * 目前只同步 video_info 和 video_info_file 表
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EsSyncCanalService
{
    /**
     * 只有这几列变了才值得让 AI 重建视频向量
     */
    private static final Set <String> AI_TEXT_COLUMNS = Set.of("video_name", "tags", "introduction");

    private final VideoInfoDocCanalTranslator videoInfoDocCanalTranslator;

    private final VideoInfoDocBulkRepository videoInfoDocBulkRepository;

    private final VideoIndexMqRepository videoIndexMqRepository;

    public void handleEntries(List <CanalEntry.Entry> entries)
    {
        // 同一 Canal 批次内按 videoId 折叠，保留最后一次操作（保持事件顺序）
        Map <Long, VideoInfoDocBulkOpt> pendingByVideoId = new LinkedHashMap <>();

        // AI 的视频向量也按 videoId 折叠，等 ES 写完再统一发消息
        Map <Long, VideoIndexTaskType> aiTaskByVideoId = new LinkedHashMap <>();

        // 字幕块的数据源是视频文件表，和上面两个各走各的
        Set <Long> subtitleTaskVideoIdSet = new LinkedHashSet <>();

        // 收集每个事件的具体操作，保存到上面三个变量中
        for (CanalEntry.Entry entry : entries)
        {
            // 不看行变更以外的消息
            if (entry.getEntryType() != CanalEntry.EntryType.ROWDATA)
            {
                continue;
            }

            // 先检查一下是不是自己负责的表
            String tableName = entry.getHeader().getTableName();
            boolean isFileTable = NiloTable.VIDEO_INFO_FILE.equalsIgnoreCase(tableName);
            if (!isFileTable && !NiloTable.VIDEO_INFO.equalsIgnoreCase(tableName))
            {
                continue;
            }

            // 将二进制字节转化为行变更事件
            CanalEntry.RowChange rowChange;
            try
            {
                rowChange = CanalEntry.RowChange.parseFrom(entry.getStoreValue());
            }
            catch (Exception e)
            {
                throw new IllegalStateException("解析 Canal RowChange 失败, table=" + tableName, e);
            }

            // 获取事件类型
            CanalEntry.EventType eventType = rowChange.getEventType();

            // 一次事件可能会改变很多行，逐行收集后统一 bulk
            for (CanalEntry.RowData rowData : rowChange.getRowDatasList())
            {
                try
                {
                    // 如果是视频文件表，我们只关心涉及了哪些视频，改了什么无所谓：字幕块本来就是按视频整体重建的
                    if (isFileTable)
                    {
                        collectSubtitleTask(subtitleTaskVideoIdSet, rowData, eventType);
                        continue;
                    }

                    // 根据事件类型收集相应操作
                    switch (eventType)
                    {
                        case INSERT, UPDATE ->
                        {
                            collectIndex(pendingByVideoId, rowData.getAfterColumnsList(), eventType);
                            collectAiTask(aiTaskByVideoId, rowData.getAfterColumnsList(), eventType);
                        }
                        case DELETE ->
                        {
                            collectDelete(pendingByVideoId, rowData.getBeforeColumnsList());
                            Long videoId = videoInfoDocCanalTranslator.extractVideoId(rowData.getBeforeColumnsList());
                            if (videoId != null)
                            {
                                aiTaskByVideoId.put(videoId, VideoIndexTaskType.DELETE);
                            }
                        }
                        default -> log.debug("忽略事件类型: {}", eventType);
                    }
                }
                catch (Exception e)
                {
                    // 单行转换失败不影响同批其它行，也不阻塞后续 Canal 批次
                    log.warn("跳过无法转换的 {} 行事件", eventType, e);
                }
            }
        }

        // 批量写入 ES：item 级失败只打日志，整次请求失败才抛异常触发 Canal rollback
        if (!pendingByVideoId.isEmpty())
        {
            List <VideoInfoDocBulkOpt> operations = new ArrayList <>(pendingByVideoId.values());
            int failCount = videoInfoDocBulkRepository.bulkExecute(operations);
            if (failCount > 0)
            {
                log.warn("ES bulk 部分失败, total={}, failed={}", operations.size(), failCount);
            }
            else
            {
                log.info("ES bulk 成功, size={}", operations.size());
            }
        }

        // 交给MQ，让 ai 包处理向量重建
        for (Map.Entry <Long, VideoIndexTaskType> task : aiTaskByVideoId.entrySet())
        {
            try
            {
                videoIndexMqRepository.sendVideoIndexTask(task.getKey(), task.getValue());
                log.info("已通知 AI 重建视频向量, videoId={}, type={}", task.getKey(), task.getValue());
            }
            catch (RuntimeException e)
            {
                log.error("通知 AI 重建视频向量失败, videoId={}, type={}", task.getKey(), task.getValue(), e);
            }
        }

        // 交给MQ，让 ai 包处理字幕块重建
        for (Long videoId : subtitleTaskVideoIdSet)
        {
            try
            {
                videoIndexMqRepository.sendSubtitleIndexTask(videoId);
                log.info("已通知 AI 重建字幕块, videoId={}", videoId);
            }
            catch (RuntimeException e)
            {
                log.error("通知 AI 重建字幕块失败, videoId={}", videoId, e);
            }
        }
    }

    /**
     * 收集要重建字幕块的视频<hr/>
     * 删除行也发重建而不是删除：视频还在不在由 video_info 说了算。审核是「全删再全插」，
     * 万一 canal 把一个事务拆成两批拉取，发删除就会出现中间态；发重建则是照库里现有的分P重新灌一遍，
     * 视频真没了就是删掉旧块再写 0 条，效果和删除一样
     */
    private void collectSubtitleTask(Set <Long> subtitleTaskVideoIdSet,
                                     CanalEntry.RowData rowData,
                                     CanalEntry.EventType eventType)
    {
        // 如果是删除就取改前的列，如果是增删就取改后的列，因为要拿 videoId，必须有数据
        List <CanalEntry.Column> columns = eventType == CanalEntry.EventType.DELETE ? rowData.getBeforeColumnsList() : rowData.getAfterColumnsList();
        Long videoId = videoInfoDocCanalTranslator.extractVideoId(columns);
        if (videoId == null)
        {
            log.warn("跳过无 video_id 的视频文件更改 {} 事件", eventType);
            return;
        }
        subtitleTaskVideoIdSet.add(videoId);
    }

    /**
     * 收集要通知 AI 重建向量的视频<hr/>
     * 只认标题、标签、简介这三列的变化。播放量、点赞数这些计数列一直在变，跟着重建纯属浪费
     */
    private void collectAiTask(Map <Long, VideoIndexTaskType> aiTaskByVideoId,
                               List <CanalEntry.Column> columns,
                               CanalEntry.EventType eventType)
    {
        Long videoId = videoInfoDocCanalTranslator.extractVideoId(columns);
        // 没有 videoId 就不收集
        if (videoId == null)
        {
            return;
        }
        // 如果是更新，不更新AI向量建立相关字段就不收集，没必要重建
        if (eventType == CanalEntry.EventType.UPDATE && columns.stream()
                                                               .noneMatch(column -> column.getUpdated() && AI_TEXT_COLUMNS.contains(
                                                                       column.getName())))
        {
            return;
        }
        aiTaskByVideoId.put(videoId, VideoIndexTaskType.UPSERT);
    }

    /**
     * 收集保存（插入或更新）操作
     *
     * @param pendingByVideoId 待写入操作（按 videoId 折叠）
     * @param columns          列数据
     * @param eventType        事件类型
     */
    private void collectIndex(Map <Long, VideoInfoDocBulkOpt> pendingByVideoId,
                              List <CanalEntry.Column> columns,
                              CanalEntry.EventType eventType)
    {
        // 转化为 VideoInfoDoc 对象
        VideoInfoDoc doc = videoInfoDocCanalTranslator.toDoc(columns);

        // 跳过无 video_id 的数据
        if (doc.getVideoId() == null)
        {
            log.warn("跳过无 video_id 的 {} 事件", eventType);
            return;
        }

        pendingByVideoId.put(doc.getVideoId(), VideoInfoDocBulkOpt.buildIndex(doc));
    }

    /**
     * 收集删除操作
     *
     * @param pendingByVideoId 待写入操作（按 videoId 折叠）
     * @param columns          列数据
     */
    private void collectDelete(Map <Long, VideoInfoDocBulkOpt> pendingByVideoId, List <CanalEntry.Column> columns)
    {
        Long videoId = videoInfoDocCanalTranslator.extractVideoId(columns);

        // 跳过无 video_id 的数据
        if (videoId == null)
        {
            log.warn("跳过无 video_id 的 DELETE 事件");
            return;
        }

        pendingByVideoId.put(videoId, VideoInfoDocBulkOpt.buildDelete(videoId));
    }
}
