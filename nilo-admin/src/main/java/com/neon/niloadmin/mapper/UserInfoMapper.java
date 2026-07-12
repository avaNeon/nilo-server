package com.neon.niloadmin.mapper;

import com.neon.nilocommon.entity.query.BaseQuery;
import com.neon.nilocommon.entity.vo.UserStatVO;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
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
     * 增加用户硬币数量
     *
     * @param userId     用户ID
     * @param coinAmount 硬币增加数量
     * @return 更改行数
     */
    Integer increaseCoin(@Param("userId") Long userId, @Param("coinAmount") Integer coinAmount);

    /**
     * 删除视频时扣减发布奖励硬币，可以扣为负数
     *
     * @param userId     用户ID
     * @param coinAmount 扣减硬币总数
     * @return 更改行数
     */
    Integer decreaseCoinForVideoDelete(@Param("userId") Long userId, @Param("coinAmount") Integer coinAmount);

    /**
     * 查询指定日期范围内的注册的用户数，按天分隔
     *
     * @param startTime 开始日期（包含）
     * @param endTime   结束日期（包含）
     * @return 数据列表
     */
    List <UserStatVO> selectStatByTimePeriod(@Param("startTime") LocalDate startTime, @Param("endTime") LocalDate endTime);
}
