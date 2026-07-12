package com.neon.nilocanalclient.repository.elasticsearch;

import com.neon.nilocanalclient.entity.BulkDocOperation;

import java.util.List;


public interface VideoInfoDocBulkRepository
{
    /**
     * 使用 BulkAPI 进行批量处理document的改动<hr/>
     * <p>按顺序执行 index / delete，同一 bulk 内各 item 独立成败</p>
     *
     * @param operations 有序操作（同一 videoId 应该在调用前合并为最后一次）
     * @return 失败的 item 数量
     * @throws IllegalStateException bulk请求失败
     */
    int bulkExecute(List <BulkDocOperation> operations);
}
