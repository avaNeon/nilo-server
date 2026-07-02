package com.neon.niloadmin.mapper;

import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 视频信息 数据库操作接口
 */
public interface VideoInfoMapper<T, P> extends BaseMapper <T, P>
{
    /**
     * 批量查询分类下有多少视频
     *
     * @param categoryIdList 分类ID列表
     * @return 视频数量
     */
    Integer selectCountByCategoryIdBatch(@Param("categoryIdList") List <Integer> categoryIdList);

    /**
     * 将指定字段增加一定的量
     *
     * @param filed     指定字段
     * @param increment 增量
     * @return 修改行数
     */
    Integer increaseByField(@Param("videoId") Long videoId, @Param("field") String filed, @Param("increment") Integer increment);

    /**
     * 将指定字段减少一定的量
     *
     * @param videoId   视频ID
     * @param filed     字段名
     * @param decrement 减量
     * @return 修改行数
     */
    Integer decreaseByField(@Param("videoId") Long videoId, @Param("field") String filed, @Param("decrement") Integer decrement);

    /**
     * 切换视频的推荐状态
     *
     * @param videoId 视频ID
     * @return 修改行数
     */
    Integer toggleRecommendType(@Param("videoId") Long videoId);

    /**
     * 按视频ID批量减少评论数量。
     */
    Integer decreaseCommentCountBatch(@Param("decreaseMap") Map <Long, Integer> decreaseMap);

    /**
     * 根据VideoId更新
     */
    Integer updateByVideoId(@Param("bean") T t, @Param("videoId") Long videoId);


    /**
     * 根据VideoId删除
     */
    Integer deleteByVideoId(@Param("videoId") Long videoId);


    /**
     * 根据VideoId获取对象
     */
    T selectByVideoId(@Param("videoId") Long videoId);
}
