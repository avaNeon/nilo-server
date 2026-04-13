package com.neon.niloweb.service;

import com.neon.nilocommon.entity.po.UserCommentAction;
import com.neon.nilocommon.entity.query.UserCommentActionQuery;
import com.neon.nilocommon.entity.vo.UserCommentActionVO;
import com.neon.niloweb.mapper.UserCommentActionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@RequiredArgsConstructor
@Service
public class UserCommentActionService
{
    private final UserCommentActionMapper <UserCommentAction, UserCommentActionQuery> userCommentActionMapper;

    /**
     * 对评论进行操作
     * @param userId 用户ID
     * @param commentId 评论ID
     * @param actionType 操作类型
     */
    public void commentAction(long userId, long commentId, int actionType)
    {

    }

    /**
     * 获取用户对评论的操作
     * @param userId 用户ID
     * @param commentId 评论ID
     * @return 所有用户对评论的操作
     */
    public List <UserCommentActionVO> getCommentAction(long userId, long commentId)
    {
        return null;
    }
}
