package com.neon.niloweb.feign.comment;

import com.neon.nilocommon.entity.vo.ResponseVO;
import com.neon.nilocommon.entity.vo.comment.CommentManagementVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@FeignClient(value = "nilo-comment", contextId = "innerVideoCommentFeignClient", path = "/inner/comment")
public interface InnerVideoCommentFeignClient
{
    @GetMapping("/creator/management/count")
    ResponseVO <Long> getCreatorCommentManagementInfoCount(@RequestParam(name = "userId") Long userId,
                                                           @RequestParam(name = "videoId", required = false) Long videoId,
                                                           @RequestParam(name = "nameFuzzy", required = false)
                                                           String nameFuzzy);

    @GetMapping("/creator/management/{pageNo}/{pageSize}")
    ResponseVO <List <CommentManagementVO>> getCreatorCommentManagementInfo(
            @PathVariable(name = "pageNo") Integer pageNo,
            @PathVariable(name = "pageSize") Integer pageSize,
            @RequestParam(name = "userId") Long userId,
            @RequestParam(name = "videoId", required = false) Long videoId,
            @RequestParam(name = "nameFuzzy", required = false) String nameFuzzy);

    @PutMapping("/user/{userId}/nickName")
    ResponseVO <Void> updateNickNameByUserId(@PathVariable(name = "userId") Long userId,
                                             @RequestParam(name = "nickName") String nickName);

    @PutMapping("/user/{userId}/avatar")
    ResponseVO <Void> updateAvatarByUserId(@PathVariable(name = "userId") Long userId,
                                           @RequestParam(name = "avatar") String avatar);

    @GetMapping("/user/{userId}/upvoteCount")
    ResponseVO <Long> getUpvoteCountByUserId(@PathVariable(name = "userId") Long userId);
}
