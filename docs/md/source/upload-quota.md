# 上传文件日额度限制

本文说明前端直传时如何限制每日上传额度。

文件上传基于MinIO presigned POST 实现前端直传，服务端不经手文件流无法直接限额；以 文件归属表 + Redis + MinIO presigned post form 上传大小限制，实现日配额严格限制。对已签发但未实际上传的 key 做复用，避免重复申请签名空耗配额。

<a href="../../../nilo-web/src/main/java/com/neon/niloweb/controller/FileController.java">对外暴露接口，81行：uploadVideo</a> -> <a href="../../../nilo-web/src/main/java/com/neon/niloweb/service/FileService.java">具体处理逻辑，190行：uploadVideo</a> ->（中间进入临界区） <a href="../../../nilo-web/src/main/java/com/neon/niloweb/service/UploadService.java">临界区，51：getUploadKey</a> ->（返回继续执行uploadVideo） <a href="../../../nilo-web/src/main/java/com/neon/niloweb/service/FileService.java">剩余处理逻辑，190行：uploadVideo</a>

下图与 [InnoDB 死锁分析](innodb-deadlock.md) 共用。

<div style="text-align: center;">
  <img src="../../img/上传视频文件流程图.drawio.svg" alt="上传视频文件流程图">
</div>
