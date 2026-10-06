# MySQL 树形评论加载优化

本文说明树形评论的加载方式。

前端参考 Reddit 评论区设计出树型评论，针对多层评论树加载（顶层 10 条 + 第一层 10 × 10 条）的 N+1 问题,先以 ROW_NUMBER() OVER (PARTITION BY parent_comment_id) 将每层查询收敛为单条 SQL ，再改用 JOIN LATERAL 将 LIMIT 下推至子查询内部，配合索引使每个父评论仅需一次索引 range 扫描；实测 EXPLAIN 中 filtered 由约 30% 提升至 100% ， SQL 执行次数由随树深度指数增长降至与深度同阶。

<a href="../../../nilo-comment/src/main/java/com/neon/nilocomment/controller/VideoCommentController.java">评论查询接口，69行：getCommentList</a> -> <a href="../../../nilo-comment/src/main/java/com/neon/nilocomment/service/VideoCommentService.java">查询过程，269行：getCommentList</a> -> <a href="../../../nilo-comment/src/main/java/com/neon/nilocomment/service/VideoCommentService.java">查询单层评论，932行：getVideoCommentListBatch</a> -> <a href="../../../nilo-comment/src/main/java/com/neon/nilocomment/mapper/VideoCommentMapper.java">查询单层评论mapper接口，42行：selectByParentIdList</a> -> <a href="../../../nilo-comment/src/main/resources/mapper/VideoCommentMapper.xml">具体SQL，793行：selectByParentIdList</a>

<div style="text-align: center;">
  <img src="../../img/树形评论加载设计.drawio.svg" alt="树形评论加载设计">
</div>
