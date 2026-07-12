package com.neon.nilocanalclient.service;

import com.alibaba.otter.canal.protocol.CanalEntry;
import com.neon.nilocanalclient.canal.VideoInfoDocCanalTranslator;
import com.neon.nilocanalclient.entity.BulkDocOperation;
import com.neon.nilocanalclient.repository.elasticsearch.VideoInfoDocBulkRepository;
import com.neon.nilocommon.entity.po.document.VideoInfoDoc;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;


@Slf4j
@Service
@RequiredArgsConstructor
public class VideoInfoCanalService
{
    private static final String TABLE_NAME = "video_info";

    private final VideoInfoDocCanalTranslator videoInfoDocCanalTranslator;

    private final VideoInfoDocBulkRepository videoInfoDocBulkRepository;

    public void handleEntries(List <CanalEntry.Entry> entries)
    {
        // 同一 Canal 批次内按 videoId 折叠，保留最后一次操作（保持事件顺序）
        Map <Long, BulkDocOperation> pendingByVideoId = new LinkedHashMap <>();

        for (CanalEntry.Entry entry : entries)
        {
            if (entry.getEntryType() != CanalEntry.EntryType.ROWDATA)
            {
                continue;
            }

            // 先检查一下是不是自己负责复制的表
            String tableName = entry.getHeader().getTableName();
            if (!TABLE_NAME.equalsIgnoreCase(tableName))
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

            // 获取事件类型
            CanalEntry.EventType eventType = rowChange.getEventType();

            // 一次事件可能会改变很多行，逐行收集后统一 bulk
            for (CanalEntry.RowData rowData : rowChange.getRowDatasList())
            {
                try
                {
                    // 根据事件类型收集相应操作
                    switch (eventType)
                    {
                        case INSERT, UPDATE -> collectIndex(pendingByVideoId, rowData.getAfterColumnsList(), eventType);
                        case DELETE -> collectDelete(pendingByVideoId, rowData.getBeforeColumnsList());
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

        if (pendingByVideoId.isEmpty())
        {
            return;
        }

        // 批量写入 ES：item 级失败只打日志，整次请求失败才抛异常触发 Canal rollback
        List <BulkDocOperation> operations = new ArrayList <>(pendingByVideoId.values());
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

    /**
     * 收集保存（插入或更新）操作
     *
     * @param pendingByVideoId 待写入操作（按 videoId 折叠）
     * @param columns          列数据
     * @param eventType        事件类型
     */
    private void collectIndex(Map <Long, BulkDocOperation> pendingByVideoId,
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

        pendingByVideoId.put(doc.getVideoId(), BulkDocOperation.toIndex(doc));
    }

    /**
     * 收集删除操作
     *
     * @param pendingByVideoId 待写入操作（按 videoId 折叠）
     * @param columns          列数据
     */
    private void collectDelete(Map <Long, BulkDocOperation> pendingByVideoId, List <CanalEntry.Column> columns)
    {
        Long videoId = videoInfoDocCanalTranslator.extractVideoId(columns);

        // 跳过无 video_id 的数据
        if (videoId == null)
        {
            log.warn("跳过无 video_id 的 DELETE 事件");
            return;
        }

        pendingByVideoId.put(videoId, BulkDocOperation.toDelete(videoId));
    }
}
