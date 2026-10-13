# MySQL 树形评论加载优化

## 简介

本文说明树形评论的加载方式。

<div style="text-align: center;">
    <img src="../../img/树形评论加载设计.drawio.svg" alt="树形评论加载设计">
    <p>整体链路图示</p>
</div>

## 设计要点

### 背景

前端参考 Reddit 评论区设计出树型评论。多层评论树加载（顶层 10 条 + 第一层 10 × 10 条）存在 N+1 问题。

### 优化过程

1. 先以 `ROW_NUMBER() OVER (PARTITION BY parent_comment_id)` 将每层查询收敛为单条 SQL；
2. 再改用 `JOIN LATERAL` 将 `LIMIT` 下推至子查询内部，配合索引使每个父评论仅需一次索引 range 扫描。

### 效果

- `EXPLAIN` 中 `filtered` 由约 30% 提升至 100%；
- SQL 执行次数由随树深度指数增长，降至与深度同阶。

## 调用链路

| 步骤 | 说明                 | 代码位置                                                                                                                                         |
|:----:|----------------------|--------------------------------------------------------------------------------------------------------------------------------------------------|
|  1   | 评论查询接口         | [`VideoCommentController#getCommentList`](../../../nilo-comment/src/main/java/com/neon/nilocomment/controller/VideoCommentController.java#L73)   |
|  2   | 查询过程             | [`VideoCommentService#getCommentList`](../../../nilo-comment/src/main/java/com/neon/nilocomment/service/VideoCommentService.java#L273)           |
|  3   | 查询单层评论         | [`VideoCommentService#getVideoCommentListBatch`](../../../nilo-comment/src/main/java/com/neon/nilocomment/service/VideoCommentService.java#L932) |
|  4   | 单层评论 mapper 接口 | [`VideoCommentMapper#selectByParentIdList`](../../../nilo-comment/src/main/java/com/neon/nilocomment/mapper/VideoCommentMapper.java#L57)         |
|  5   | 具体 SQL             | [`VideoCommentMapper.xml#selectByParentIdList`](../../../nilo-comment/src/main/resources/mapper/VideoCommentMapper.xml#L643)                     |
