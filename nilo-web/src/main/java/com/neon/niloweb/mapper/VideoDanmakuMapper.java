package com.neon.niloweb.mapper;

import com.neon.nilocommon.entity.vo.danmaku.DanmakuManagementVO;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 视频弹幕 数据库操作接口
 */
public interface VideoDanmakuMapper<T, P> extends BaseMapper <T, P>
{

    /**
     * 获取弹幕管理信息统计
     */
    Long selectDanmakuManagementVOCount(@Param("userId") Long userId,
                                        @Param("videoId") Long videoId,
                                        @Param("fileIndex") Integer fileIndex,
                                        @Param("nameFuzzy") String nameFuzzy);

    /**
     * 获取弹幕管理信息
     */
    List <DanmakuManagementVO> selectDanmakuManagementVO(@Param("userId") Long userId,
                                                         @Param("videoId") Long videoId,
                                                         @Param("fileIndex") Integer fileIndex,
                                                         @Param("nameFuzzy") String nameFuzzy,
                                                         @Param("start") Integer start,
                                                         @Param("pageSize") Integer pageSize);

    /**
     * 根据DanmakuId更新
     */
    Integer updateByDanmakuId(@Param("bean") T t, @Param("danmakuId") Long danmakuId);


    /**
     * 根据DanmakuId删除
     */
    Integer deleteByDanmakuId(@Param("danmakuId") Long danmakuId);


    /**
     * 根据DanmakuId获取对象
     */
    T selectByDanmakuId(@Param("danmakuId") Long danmakuId);


}
