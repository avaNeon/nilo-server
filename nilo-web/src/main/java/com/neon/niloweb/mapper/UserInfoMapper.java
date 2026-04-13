package com.neon.niloweb.mapper;

import com.neon.nilocommon.entity.query.BaseQuery;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 用户信息 数据库操作接口
 */
public interface UserInfoMapper<T, P extends BaseQuery> extends BaseMapper <T, P>
{

    /**
     * 根据UserId更新<hr/>
     * 所有的更新操作都只修改非空字段
     */
    Integer updateByUserId(@Param("bean") T t, @Param("userId") Long userId);

    /**
     * 根据UserId删除
     */
    Integer deleteByUserId(@Param("userId") Long userId);

    /**
     * 根据UserId获取对象
     */
    T selectByUserId(@Param("userId") Long userId);

    /**
     * 通过userId批量查找用户信息
     *
     * @param userIdList userId列表
     * @return 用户信息列表
     */
    List <T> selectBatchByUserId(@Param("userIdList") List <Long> userIdList);

    /**
     * 根据Email更新
     */
    Integer updateByEmail(@Param("bean") T t, @Param("email") String email);

    /**
     * 根据Email删除
     */
    Integer deleteByEmail(@Param("email") String email);

    /**
     * 根据Email获取对象
     */
    T selectByEmail(@Param("email") String email);

    /**
     * 根据NickName更新
     */
    Integer updateByNickName(@Param("bean") T t, @Param("nickName") String nickName);

    /**
     * 根据NickName删除
     */
    Integer deleteByNickName(@Param("nickName") String nickName);

    /**
     * 根据NickName获取对象
     */
    T selectByNickName(@Param("nickName") String nickName);

    /**
     * 增加用户硬币数量
     * @param userId 用户ID
     * @param coinAmount 硬币增加数量
     * @return 更改行数
     */
    Integer increaseCoin(@Param("userId") Long userId, @Param("coinAmount") Short coinAmount);

    /**
     * 减少用户的硬币数量
     * @param userId 用户ID
     * @param coinAmount 扣减硬币总数
     * @return 更改行数（如果为0说明用户ID不对，或者用户硬币余额不足）
     */
    Integer decreaseCoin(@Param("userId") Long userId, @Param("coinAmount") Short coinAmount);
}
