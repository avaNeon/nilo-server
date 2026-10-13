# 播放量记录统计链路

## 简介

本文说明播放量的记录与统计链路。

> 图片为 svg 格式，建议下载后在浏览器中查看。

<div style="text-align: center;">
    <img src="../../img/播放统计流程图.drawio.svg" alt="播放统计流程图">
    <p>整体链路图示</p>
</div>

## 流程

### 记录链路

接口仅 `offer` 入队无锁队列即返回，定时任务每 5s 聚合为 `<视频ID, 增量>` 并分批投递 MQ，记录与统计经 MQ 解耦。

| 步骤 | 说明              | 代码位置                                                                                                             |
|:----:|-------------------|----------------------------------------------------------------------------------------------------------------------|
|  1   | 接口入口          | [`VideoController#playCount`](../../../nilo-web/src/main/java/com/neon/niloweb/controller/VideoController.java#L121) |
|  2   | 入队无锁队列      | [`VideoService#playCount`](../../../nilo-web/src/main/java/com/neon/niloweb/service/VideoService.java#L257)          |
|  3   | 聚合并分批投递 MQ | [`VideoService#sendPlayCount`](../../../nilo-web/src/main/java/com/neon/niloweb/service/VideoService.java#L426)      |

### 统计链路

消费端（可选）并发消费，并行双写 MySQL / Redis，DB 写入完成后再 ACK 以削峰。

- **MySQL**：批量小部分数据循环 + `CASE ... WHEN ...` 语法，使错误范围与 RTT 损耗折中。
- **Redis**：使用 Lua 脚本循环写入以减少 RTT，内部使用 `pcall` 避免单条数据操作失败拖垮整组。

| 步骤 | 说明                   | 代码位置                                                                                                                                                           |
|:----:|------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------|
|  1   | 消费 MQ 消息           | [`PlayCountConsumer#consume`](../../../nilo-mq-consumer/src/main/java/com/neon/nilomqconsumer/consumer/PlayCountConsumer.java#L19)                                 |
|  2   | 并行双写，等待两者完成 | [`PlayCountService#flushPlayCount`](../../../nilo-mq-consumer/src/main/java/com/neon/nilomqconsumer/service/PlayCountService.java#L16)                             |
|  3   | 并行写入 MySQL         | [`PlayCountAsyncService#flushPlayCountBatchToMysql`](../../../nilo-mq-consumer/src/main/java/com/neon/nilomqconsumer/service/async/PlayCountAsyncService.java#L28) |
|  3   | 并行写入 Redis         | [`PlayCountAsyncService#flushPlayCountBatchToRedis`](../../../nilo-mq-consumer/src/main/java/com/neon/nilomqconsumer/service/async/PlayCountAsyncService.java#L69) |

## 实测数据

为上述各版本设计了集成测试，压测结果见 [播放统计各版本对比](../eval/perf-play-count/eval.md)。
