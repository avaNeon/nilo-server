package com.neon.niloweb.mapper;

import org.apache.ibatis.annotations.Param;

/**
 * 用于为用户请求上传视频key的行为加锁 数据库操作接口
 */
public interface UserUploadVideoLockMapper<T, P> extends BaseMapper <T, P>
{
    /**
     * 尝试为用户请求上传视频key的行为加锁<hr/>
     * <p>当用户第一次在这个表中记录，且并发数>3，且先执行的事务发生回滚时，<br/>
     * 将会发生死锁，其中一个事务将会被回滚</p>
     * <p>正常用户的上传操作都是串行化的，不太可能出现这个情况，但是还是要小心失败情况的处理</p>
     *
     * @param userId 用户id
     * @return 1:拿到锁 0:没拿到锁
     */
    Integer tryLock(@Param("userId") Long userId);

    /**
     * 查询当前的InnoDB锁等待超时时间
     * @return InnoDB锁等待超时时间
     */
    Integer selectInnodbLockWaitTimeout();

    /**
     * 设置当前会话的InnoDB锁等待超时时间
     * @param timeout 超时时间
     */
    void setSessionInnodbLockWaitTimeout(@Param("timeout") Integer timeout);
}
