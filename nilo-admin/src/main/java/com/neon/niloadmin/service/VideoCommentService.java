package com.neon.niloadmin.service;

import com.neon.niloadmin.feign.comment.InnerAdminVideoCommentFeignClient;
import com.neon.nilocommon.entity.enums.ResponseCode;
import com.neon.nilocommon.entity.vo.ResponseVO;
import com.neon.nilocommon.entity.vo.comment.CommentManagementAdmin;
import com.neon.nilocommon.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@RequiredArgsConstructor
@Service
public class VideoCommentService
{
    private final InnerAdminVideoCommentFeignClient innerAdminVideoCommentFeignClient;

    public Long getCommentManagementInfoCount(String nameFuzzy)
    {
        ResponseVO <Long> result = innerAdminVideoCommentFeignClient.getAdminCommentManagementInfoCount(nameFuzzy);
        assertSuccess(result);
        return result.getData();
    }

    public List <CommentManagementAdmin> getCommentManagementInfo(String nameFuzzy, int pageNo, int pageSize)
    {
        ResponseVO <List <CommentManagementAdmin>> result =
                innerAdminVideoCommentFeignClient.getAdminCommentManagementInfo(pageNo, pageSize, nameFuzzy);
        assertSuccess(result);
        return result.getData();
    }

    /**
     * 逻辑删除指定评论。
     */
    public void deleteComment(long commentId)
    {
        assertSuccess(innerAdminVideoCommentFeignClient.deleteCommentByAdmin(commentId));
    }

    /**
     * 真正删除指定已逻辑删除的评论。
     */
    public void destroyComment(long commentId)
    {
        assertSuccess(innerAdminVideoCommentFeignClient.destroyComment(commentId));
    }

    /**
     * 真正删除发布时间在指定日期范围内且已逻辑删除的评论
     *
     * @param postTimeStart 开始时间
     * @param postTimeEnd   结束时间（均包含）
     */
    public void destroyCommentsByPostTimeRange(LocalDate postTimeStart, LocalDate postTimeEnd)
    {
        assertSuccess(innerAdminVideoCommentFeignClient.destroyCommentsByPostTimeRange(postTimeStart, postTimeEnd));
    }

    private void assertSuccess(ResponseVO <?> result)
    {
        if (result == null || !ResponseCode.SUCCESS.getCode().equals(result.getCode()))
        {
            throw new BusinessException(result == null ? "评论服务调用失败" : result.getInfo());
        }
    }
}
