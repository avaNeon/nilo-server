# 播放量记录统计链路

本文说明播放量的记录与统计链路。

记录链路：接口仅 offer 入队无锁队列即返回，定时任务每 5s 聚合为 &lt;视频ID, 增量&gt; 并分批投递 MQ，记录与统计经 MQ 解耦。

统计链路：消费端（可选）并发消费，并行双写 MySQL/Redis ， DB 写入完成后再 ACK 以削峰。 MySQL 使用 批量小部分数据循环 + CASE … WHEN … 语法使错误范围与 RTT 损耗折中。 Redis 使用 Lua 脚本循环写入以减少 RTT ，内部使用 pcall 避免单条数据操作失败拖垮整组。

记录链路：<a href="../../../nilo-web/src/main/java/com/neon/niloweb/controller/VideoController.java">107行：playCount</a> -> <a href="../../../nilo-web/src/main/java/com/neon/niloweb/service/VideoService.java">244行：playCount</a> -> <a href="../../../nilo-web/src/main/java/com/neon/niloweb/service/VideoService.java">402行：sendPlayCount</a>

统计链路：<a href="../../../nilo-mq-consumer/src/main/java/com/neon/nilomqconsumer/consumer/PlayCountConsumer.java">consumer</a> -> <a href="../../../nilo-mq-consumer/src/main/java/com/neon/nilomqconsumer/service/PlayCountService.java">service</a> -> <a href="../../../nilo-mq-consumer/src/main/java/com/neon/nilomqconsumer/service/async/PlayCountAsyncService.java">flushPlayCountBatchToMysql和flushPlayCountBatchToRedis并行双写DB</a>

<div style="text-align: center;">
  <img src="../../img/播放统计流程图.drawio.svg" alt="播放统计流程图">
</div>

各版本的压测结果见 [播放统计各版本对比](../eval/perf-play-count/eval.md)。
