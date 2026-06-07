package com.neon.niloweb.mapper;

import com.neon.nilocommon.entity.dto.UserMessageCount;
import com.neon.nilocommon.entity.dto.UserMessageDTO;
import com.neon.nilocommon.entity.po.userMessage.UserMessage;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 用户消息表 数据库操作接口
 */
public interface UserMessageMapper<T, P> extends BaseMapper <T, P>
{

    /**
     * <b>插入一条数据</b><hr/>
     * <p>如果数据重复（userId, videoId, messageType, senderUserId都不是null且相同），则忽略</p>
     *
     * @param userMessage 用户信息
     * @return 修改行数
     */
    Integer insertIfNotExists(@Param("userMessage") UserMessage userMessage);

    /**
     * <b>查询各分类未读消息数量</b>
     *
     * @param userId 用户ID
     * @return 各分类未读消息统计
     */
    UserMessageCount selectUncheckedMessageCount(@Param("userId") Long userId);

    /**
     * <b>将指定分类的消息都标记为已读</b>
     *
     * @param userId      用户ID
     * @param messageType 消息类型
     * @return 改变行数
     */
    Integer checkAllMessages(@Param("userId") Long userId, @Param("messageType") Short messageType);

    /**
     * <b>将指定消息标记为已读</b>
     *
     * @param userId    用户ID
     * @param messageId 消息ID
     * @return 改变行数
     */
    Integer checkMessage(@Param("userId") Long userId, @Param("messageId") Long messageId);

    /**
     * <b>根据用户ID和消息ID删除一条数据</b>
     *
     * @param userId    用户ID
     * @param messageId 消息ID
     * @return 改变行数
     */
    Integer deleteByUserIdAndMessageId(@Param("userId") Long userId, @Param("messageId") Long messageId);

    List<UserMessageDTO> selectDtoList(@Param("query") P p);

    /**
     * 根据MessageId更新
     */
    Integer updateByMessageId(@Param("bean") T t, @Param("messageId") Long messageId);


    /**
     * 根据MessageId删除
     */
    Integer deleteByMessageId(@Param("messageId") Long messageId);


    /**
     * 根据MessageId获取对象
     */
    T selectByMessageId(@Param("messageId") Long messageId);


}
