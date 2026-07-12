package com.neon.niloweb.mapper;

import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 数据库操作接口
 */
public interface MediaOwnershipMapper<T, P> extends BaseMapper <T, P>
{
    /**
     * 查询过期记录并加行锁
     *
     * @param now        现在时间
     * @param expireHour 过期小时数
     * @return 过期记录列表
     */
    List <T> selectExpiredForUpdate(@Param("now") LocalDateTime now, @Param("expireHour") Integer expireHour);

    /**
     * 删除过期记录
     *
     * @param now        现在时间
     * @param expireHour 过期小时数
     * @return 受影响的行数
     */
    Integer deleteExpiredRecord(@Param("now") LocalDateTime now, @Param("expireHour") Integer expireHour);

    /**
     * 根据ObjectKey、ownerId和used获取对象
     */
    T selectByObjectKeyAndOwnerIdAndUsed(@Param("objectKey") String objectKey,
                                         @Param("ownerId") Long ownerId,
                                         @Param("used") Integer used);

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
     * 批量将指定用户的多个未使用记录标记为已使用<hr/>
     * 条件：object_key在列表中、owner_id匹配、used=0
     *
     * @param objectKeys 对象key列表
     * @param ownerId    所有者用户ID
     * @param usedTime   使用时间
     * @return 受影响的行数（应等于objectKeys.size()，否则说明部分记录不存在或已被使用）
     */
    Integer markAsUsedBatch(@Param("objectKeys") List <String> objectKeys,
                            @Param("ownerId") Long ownerId,
                            @Param("usedTime") LocalDateTime usedTime);

    /**
     * 批量查询指定用户多个objectKey中满足条件的记录数<hr/>
     * 用于校验一批key是否全部属于该用户且处于指定used状态
     *
     * @param objectKeys 对象key列表
     * @param ownerId    所有者用户ID
     * @param used       used状态值
     * @return 匹配的记录数（应等于objectKeys.size()，否则说明部分记录不存在或不满足条件）
     */
    Integer selectCountByObjectKeysAndOwnerIdAndUsed(@Param("objectKeys") List <String> objectKeys,
                                                     @Param("ownerId") Long ownerId,
                                                     @Param("used") Integer used);

    /**
     * 根据属主ID和bucket获取最后一条记录
     *
     * @param ownerId 所有者用户ID
     * @param bucket  存储桶名称
     * @return 最后一条记录
     */
    T selectLastByOwnerIdAndBucket(@Param("ownerId") Long ownerId, @Param("bucket") String bucket);

    /**
     * 根据Id更新创建时间
     *
     * @param id          主键ID
     * @param createdTime 创建时间
     * @return 受影响的行数
     */
    Integer updateCreatedTimeById(@Param("id") Long id, @Param("createdTime") LocalDateTime createdTime);

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
