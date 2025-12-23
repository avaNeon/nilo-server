package com.neon.niloweb.mapper;

import com.neon.nilocommon.entity.query.BaseQuery;
import org.apache.ibatis.annotations.Param;

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

}
