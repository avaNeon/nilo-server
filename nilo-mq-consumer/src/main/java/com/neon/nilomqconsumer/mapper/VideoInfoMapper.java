package com.neon.nilomqconsumer.mapper;

import com.neon.nilocommon.entity.po.VideoInfo;
import com.neon.nilocommon.entity.query.VideoInfoQuery;
import com.neon.nilocommon.entity.vo.VideoSeriesVideoVO;
import com.neon.nilocommon.entity.vo.videoInfo.BriefVideoInfoVO;
import com.neon.nilocommon.entity.vo.videoInfo.VideoInfoVO;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 视频信息 数据库操作接口
 */
public interface VideoInfoMapper<T, P> extends BaseMapper <T, P>
{

    /**
     * 通过video_id列表查询BriefVideoInfoVO列表
     *
     * @param videoIdList video_id 列表
     * @return BriefVideoInfoVO列表
     */
    List <BriefVideoInfoVO> selectBriefVoListByVideoIdBatch(@Param("videoIdList") List <Long> videoIdList);

    /**
     * 通过参数查询BriefVideoInfoVO列表
     *
     * @param videoInfoQuery 参数
     * @return BriefVideoInfoVO列表
     */
    List <BriefVideoInfoVO> selectBriefVoListByParam(@Param("query") VideoInfoQuery videoInfoQuery);

    /**
     * 通过videoId查询VideoInfoVO
     *
     * @param videoId 视频ID
     * @return VideoInfoVO
     */
    VideoInfoVO selectVoByVideoId(@Param("videoId") Long videoId);

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
     * 喜欢+1
     *
     * @param videoId 视频ID
     * @return 修改行数
     */
    Integer increaseLikeCount(@Param("videoId") Long videoId);

    Integer decreaseLikeCount(@Param("videoId") Long videoId);

    Integer increaseCollectCount(@Param("videoId") Long videoId);

    Integer decreaseCollectCount(@Param("videoId") Long videoId);

    Integer increaseCoinCount(@Param("videoId") Long videoId, @Param("coinAmount") Short coinAmount);

    /**
     * 通过用户ID和视频ID列表查询对应记录数量
     *
     * @param userId      用户ID
     * @param videoIdList 视频ID列表
     * @return 记录数量
     */
    Integer selectCountByUserIdAndVideoIdList(@Param("userId") Long userId, @Param("videoIdList") List <Long> videoIdList);

    /**
     * 根据userId分页查询不在seriesId合集的视频数量
     *
     * @param userId   用户ID
     * @param seriesId 合集ID
     * @return 视频数量
     */
    Integer selectCountVideoInfoByUserIdExcludingSeriesId(@Param("userId") Long userId, @Param("seriesId") Long seriesId);

    /**
     * 根据userId分页查询不在seriesId合集的视频 <hr/>
     * 按更新时间倒序排列
     *
     * @param userId   用户ID
     * @param seriesId 合集ID
     * @param start    起始记录序号
     * @param pageSize 页大小
     * @return 视频列表
     */
    List <VideoInfo> selectVideoInfoByUserIdExcludingSeriesId(@Param("userId") Long userId,
                                                              @Param("seriesId") Long seriesId,
                                                              @Param("start") Integer start,
                                                              @Param("pageSize") Integer pageSize);

    /**
     * 根据seriesId分页查询对应的视频信息
     *
     * @param seriesId 合集ID
     * @param start    起始记录序号
     * @param pageSize 页大小
     * @return 视频列表
     */
    List <VideoInfo> selectVideoInfoBySeriesId(@Param("seriesId") Long seriesId,
                                               @Param("start") Integer start,
                                               @Param("pageSize") Integer pageSize);

    /**
     * 根据seriesId列表批量查找videoInfo
     *
     * @param seriesIdList seriesId列表
     * @param pageSize     限制长度
     * @return videoInfo列表
     */
    List <VideoSeriesVideoVO> selectVideoInfoBySeriesIdBatch(@Param("seriesIdList") List <Long> seriesIdList,
                                                             @Param("pageSize") Integer pageSize);

    List <VideoInfo> selectVideoInfoByVideoIdBatch(@Param("videoIds") List <Long> videoIds);

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
