package com.neon.nilocomment.mapper;

import org.apache.ibatis.annotations.Param;

/**
 * 评论存档 数据库操作接口
 */
public interface VideoCommentArchiveMapper<T, P> extends BaseMapper <T, P>
{

    /**
     * 根据CommentId更新
     */
    Integer updateByCommentId(@Param("bean") T t, @Param("commentId") Long commentId);


    /**
     * 根据CommentId删除
     */
    Integer deleteByCommentId(@Param("commentId") Long commentId);


    /**
     * 根据CommentId获取对象
     */
    T selectByCommentId(@Param("commentId") Long commentId);

    /**
     * 按评论者 user_id 批量更新冗余昵称
     */
    Integer updateNickNameByUserId(@Param("userId") Long userId, @Param("nickName") String nickName);

    /**
     * 按被回复者 reply_user_id 批量更新冗余回复昵称
     */
    Integer updateReplyNickNameByReplyUserId(@Param("replyUserId") Long replyUserId,
                                             @Param("replyNickName") String replyNickName);

    /**
     * 按评论者 user_id 批量更新冗余头像
     */
    Integer updateAvatarByUserId(@Param("userId") Long userId, @Param("avatar") String avatar);

    /**
     * 按 video_id 批量更新冗余视频标题
     */
    Integer updateVideoNameByVideoId(@Param("videoId") Long videoId, @Param("videoName") String videoName);

    /**
     * 按 video_id 批量更新冗余视频封面
     */
    Integer updateVideoCoverByVideoId(@Param("videoId") Long videoId, @Param("videoCover") String videoCover);

}
