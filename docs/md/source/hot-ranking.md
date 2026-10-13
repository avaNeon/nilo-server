# Redis 实现 24 小时热度视频排行榜

## 简介

本文说明 24 小时热度排行榜的存储与冷热分离。

> 图片为 svg 格式，建议下载后在浏览器中查看。

<div style="text-align: center;">
    <img src="../../img/redis视频热度统计.drawio.svg" alt="Redis 视频热度统计">
    <p>整体链路图示</p>
</div>

## 设计要点

### 存储结构优化

将单个视频统计结构从“1 个播放记录 1 个 key”重构为按小时桶保存当小时播放量，降低并固定单个视频热度的存储占用：1K 视频平均
100K 播放量的存储开销从 10GB 左右降低至固定 380KB 左右。

每个视频对应一个 `ZSET`（key 为 `HOT_VIDEO_COUNTING_PREFIX + videoId`）： **member 是小时桶**（自 1970-01-01 起的小时数，如
`497739`）， **score 是该小时的播放量**。

#### 计算逻辑

> 以下为估算值，实际以 Redis 的 `MEMORY USAGE <key>` 为准。
>
> 前提条件：Redis 7.x + jemalloc；videoId 为 19 位雪花 ID；单小时播放量不超过 `int` 范围。

**一、旧方案：1 个播放记录 1 个 key**

| 项目                                                                   | 大小                                                  |
|------------------------------------------------------------------------|-------------------------------------------------------|
| key：前缀 `nilo:{play-count}:counting:`（27 字符）+ videoId（19 字符） | 46 字符，加 SDS 头与结尾符约 50 B，jemalloc 分配 64 B |
| value：`1`（小整数，使用共享对象）                                     | 不额外占用                                            |
| 哈希表项（`dictEntry` 24 B，分配 32 B）+ 哈希桶                        | 约 40 B                                               |
| **单条记录合计**                                                       | 约 **100 B**                                          |

这里还没有计入为保证 key 唯一而必须追加的后缀，所以是下限。

1K 个视频，每个 100K 播放量：1K × 100K = 10⁸ 条记录，10⁸ × 100B = 10¹⁰ B ≈ **10GB**（仅按 key + value 本身计算也有 1K ×
100K × 46B ≈ 4.6GB）。

**二、新方案：每个视频 1 个 ZSET，25 个小时桶**

当前小时 + 过去 24 小时，共 25 个 member。

Redis 对元素数不超过 128 且 member 不超过 64 B 的 `ZSET` 使用 listpack 紧凑编码：member 与 score 依次作为相邻的 entry
存放，且 **规范的整数字符串会被存成整数**。每个 entry 大小 = 编码头 + 数据 + 1 B 回溯长度。

| 项目                                                          | 大小                          |
|---------------------------------------------------------------|-------------------------------|
| member：小时数（如 `497739`），超出 16 位、落在 24 位整数范围 | 4 + 1 = 5 B                   |
| score：小时播放量，按 16 位整数估算（≤ 32767）                | 3 + 1 = 4 B                   |
| listpack 本体：头 6 B + 结束符 1 B + 25 × (5 + 4) B           | 232 B，jemalloc 分配 256 B    |
| key：46 字符，约 50 B，jemalloc 分配 64 B                     | 64 B                          |
| 对象头 `robj` 16 B + 哈希表项 32 B + 哈希桶 8 B               | 56 B                          |
| **单个视频合计**                                              | 约 **380 B**（256 + 64 + 56） |

score 取值范围对 listpack 本体大小的影响：

| 小时播放量            | score 占用 | listpack 本体 |
|-----------------------|------------|---------------|
| < 128                 | 2 B        | 182 B         |
| < 32768               | 4 B        | 232 B         |
| < 8388608             | 5 B        | 257 B         |
| 到 `int` 上限（最坏） | 6 B        | 282 B         |

不论 score 取到哪一档，单个视频都在 450 B 以内。 **占用只取决于视频数，与播放量无关**：1K 个视频就是 1K × 380B ≈ **380KB**。

全站视频热度总排行榜使用 `ZSET` 维护，固定时间聚合各个视频的小时桶记录。

### 冷热分离

- **冷表**：只统计 1 小时播放记录，每个视频的 `ZSET` 只有 1 个 member，listpack 本体约 16 B，单个视频合计约 140 B； **热表**
  ：统计 24 小时播放记录，单个视频约 380 B。
  冷表的 listpack 本体比热表小约 14 倍（232 B → 16 B），但 key 与对象头等固定开销约 120 B 不随 member 数量变化，所以单个视频整体约降低
  2.7 倍。
- 设置固定的升级 / 降级阈值，阈值之间设计迟滞区间，避免记录抖动。
- 冷热迁移拆分为 4 步，保证自愈。

## 调用链路

| 类型     | 定时任务入口                                                                                                   | 处理逻辑                                                                                                                                         |
|----------|----------------------------------------------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------|
| 冷表刷新 | [`HotVideoTask#refreshColdCount`](../../../nilo-web/src/main/java/com/neon/niloweb/task/HotVideoTask.java#L20) | [`HotVideoRedisRepository#refreshColdCount`](../../../nilo-web/src/main/java/com/neon/niloweb/repository/redis/HotVideoRedisRepository.java#L51) |
| 热表刷新 | [`HotVideoTask#refreshHotCount`](../../../nilo-web/src/main/java/com/neon/niloweb/task/HotVideoTask.java#L29)  | [`HotVideoRedisRepository#refreshHotCount`](../../../nilo-web/src/main/java/com/neon/niloweb/repository/redis/HotVideoRedisRepository.java#L203) |

相关 Lua 脚本（带注释）见 [docs/script](../../script)。
