package com.neon.niloadmin.feign.comment;

import com.neon.nilocommon.entity.vo.ResponseVO;
import com.neon.nilocommon.entity.vo.comment.CommentManagementAdmin;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.util.List;

@FeignClient(value = "nilo-comment", contextId = "innerAdminVideoCommentFeignClient", path = "/inner/comment/admin")
public interface InnerAdminVideoCommentFeignClient
{
    @GetMapping("/management/count")
    ResponseVO <Long> getAdminCommentManagementInfoCount(@RequestParam(name = "nameFuzzy", required = false) String nameFuzzy);

    @GetMapping("/management/{pageNo}/{pageSize}")
    ResponseVO <List <CommentManagementAdmin>> getAdminCommentManagementInfo(@PathVariable(name = "pageNo") Integer pageNo,
                                                                             @PathVariable(name = "pageSize") Integer pageSize,
                                                                             @RequestParam(name = "nameFuzzy", required = false)
                                                                             String nameFuzzy);

    @DeleteMapping("/{commentId}")
    ResponseVO <Void> deleteCommentByAdmin(@PathVariable(name = "commentId") Long commentId);

    @DeleteMapping("/destroy/{commentId}")
    ResponseVO <Void> destroyComment(@PathVariable(name = "commentId") Long commentId);

    @DeleteMapping("/destroy/range")
    ResponseVO <Void> destroyCommentsByPostTimeRange(
            @RequestParam(name = "postTimeStart") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate postTimeStart,
            @RequestParam(name = "postTimeEnd") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate postTimeEnd);
}
