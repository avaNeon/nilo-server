package com.neon.nilomqconsumer.mapper;

import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 数据库操作接口
 */
public interface MediaOwnershipMapper<T, P> extends BaseMapper <T, P>
{
    /**
     * 将指定用户和objectKey的未使用记录标记为已使用<hr/>
     * 条件：object_key匹配、owner_id匹配、used=0
     *
     * @param objectKey 对象key
     * @param ownerId   所有者用户ID
     * @param usedTime  使用时间
     * @return 受影响的行数（0表示无匹配记录，可能已被标记或不存在）
     */
    Integer markAsUsed(@Param("objectKey") String objectKey,
                       @Param("ownerId") Long ownerId,
                       @Param("usedTime") LocalDateTime usedTime);

    /**
     * 将指定用户和objectKey的未使用记录标记为未使用<hr/>
     * 条件：object_key匹配、owner_id匹配、used=0
     *
     * @param objectKey 对象key
     * @param ownerId   所有者用户ID
     * @return 受影响的行数（0表示无匹配记录，可能已被标记或不存在）
     */
    Integer markAsUnused(@Param("objectKey") String objectKey,
                          @Param("ownerId") Long ownerId);

    /**
     * 根据ObjectKey更新
     */
    Integer updateByObjectKey(@Param("bean") T t, @Param("objectKey") String objectKey);


    /**
     * 根据ObjectKey删除
     */
    Integer deleteByObjectKey(@Param("objectKey") String objectKey);

    /**
     * 根据ObjectKey批量删除
     */
    Integer deleteBatchByObjectKey(@Param("objectKeys") List <String> objectKeys);


    /**
     * 根据ObjectKey获取对象
     */
    T selectByObjectKey(@Param("objectKey") String objectKey);

    /**
     * 根据Id更新
     */
    Integer updateById(@Param("bean") T t, @Param("id") Long id);


    /**
     * 根据Id删除
     */
    Integer deleteById(@Param("id") Long id);


    /**
     * 根据Id获取对象
     */
    T selectById(@Param("id") Long id);
}
