# 设计与实现

本目录说明 Nilo 的模块划分与核心实现。

## 文档目录

| 文档                                     | 内容                                  |
|------------------------------------------|---------------------------------------|
| [architecture.md](architecture.md)       | 整体架构：各模块职责                  |
| [innodb-deadlock.md](innodb-deadlock.md) | InnoDB 死锁：并发上传时的锁升级       |
| [upload-quota.md](upload-quota.md)       | 上传日配额：直传限额与未上传 key 复用 |
| [video-upload.md](video-upload.md)       | 视频上传链路：上传、转码、审核与发布  |
| [play-count.md](play-count.md)           | 播放量统计：入队记录与消费端双写      |
| [hot-ranking.md](hot-ranking.md)         | 24 小时热度：小时桶、冷热分离与排行榜 |
| [comment-tree.md](comment-tree.md)       | 树形评论：多层加载的 SQL 收敛         |
