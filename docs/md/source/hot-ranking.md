# Redis 实现24 小时热度视频排行榜

本文说明 24 小时热度排行榜的存储与冷热分离。

储存结构优化：将单个视频统计结构从 “ 1 个播放记录 1 个 key ” 重构为按照小时桶保存当小时播放量，降低并固定单个视频热度存储占用， 1K 视频平均 100K 播放量的储存开销从 1GB 左右降低至固定 250KB 左右。全站视频热度总排行榜使用 ZSET 维护，固定时间聚合各个视频小时桶记录。

24 小时热度记录冷热分离：冷表只统计 1 小时播放记录，热表统计 24 小时播放记录，储存占用再降低 10 倍。设置固定升级/降级阈值，阈值之间设计迟滞区间避免记录抖动。冷热迁移步骤拆分 4 步保证自愈。

<a href="../../../nilo-web/src/main/java/com/neon/niloweb/task/HotVideoTask.java">冷表刷新接口，16行：refreshColdCount；热表刷新接口，25行：refreshHotCount</a> -> <a href="../../../nilo-web/src/main/java/com/neon/niloweb/repository/redis/HotVideoRedisRepository.java">冷表处理逻辑，48行：refreshColdCount；热表处理逻辑，200行：refreshHotCount</a>

<a href="../../script">相关lua脚本（带注释）</a>

<div style="text-align: center;">
  <img src="../../img/redis视频热度统计.drawio.svg" alt="Redis 视频热度统计">
</div>
