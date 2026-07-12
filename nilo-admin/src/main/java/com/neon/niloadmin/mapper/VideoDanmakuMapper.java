package com.neon.niloadmin.mapper;

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
    Long selectDanmakuCount(@Param("nameFuzzy") String nameFuzzy);

    /**
     * 获取弹幕管理信息
     */
    List <DanmakuManagementVO> selectDanmakuManagementVO(@Param("nameFuzzy") String nameFuzzy,
                                                         @Param("start") Integer start,
                                                         @Param("pageSize") Integer pageSize);

    /**
     * 通过 fileId 列表批量删除弹幕
     *
     * @param fileIdList fileId 列表
     * @return 删除行数
     */
    Integer deleteByFileIdBatch(@Param("fileIdList") List <Long> fileIdList);


    /**
     * 根据DanmakuId删除
     */
    Integer deleteByDanmakuId(@Param("danmakuId") Long danmakuId);


    /**
     * 根据DanmakuId获取对象
     */
    T selectByDanmakuId(@Param("danmakuId") Long danmakuId);


}
