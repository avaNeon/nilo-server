# InnoDB 死锁分析

本文说明单用户并发上传场景下的 InnoDB 死锁。

定位并分析单用户并发上传场景下的InnoDB 死锁：多个事务执行INSERT ... ON DUPLICATE KEY UPDATE，且目标行不存在时，如果先插入的事务回滚了，会同时唤醒其它事务，并将他们的record 锁转为gap 锁（唯一索引），彼此持有兼容gap 锁后升级insert intention 锁形成死锁(并发数≥3 触发)。

<a href="../../../nilo-web/src/main/java/com/neon/niloweb/mapper/UserUploadVideoLockMapper.java">10行：tryLock</a> -> <a href="../../../nilo-web/src/main/resources/mapper/UserUploadVideoLockMapper.xml">129行：tryLock</a>

下图与 [上传文件日额度限制](upload-quota.md) 共用。

<div style="text-align: center;">
  <img src="../../img/上传视频文件流程图.drawio.svg" alt="上传视频文件流程图">
</div>
