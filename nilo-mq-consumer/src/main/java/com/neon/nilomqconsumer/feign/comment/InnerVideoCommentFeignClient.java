package com.neon.nilomqconsumer.feign.comment;

import com.neon.nilocommon.entity.dto.comment.CommentDailyStatisticsDTO;
import com.neon.nilocommon.entity.vo.ResponseVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.util.List;

@FeignClient(value = "nilo-comment", contextId = "innerVideoCommentFeignClient", path = "/inner/comment")
public interface InnerVideoCommentFeignClient
{
    @PostMapping("/archive/{videoId}")
    ResponseVO <Void> archiveByVideoId(@PathVariable(name = "videoId") Long videoId);

    @PostMapping("/restore/{videoId}")
    ResponseVO <Void> restoreByVideoId(@PathVariable(name = "videoId") Long videoId);

    @DeleteMapping("/archive/{videoId}")
    ResponseVO <Void> purgeArchiveByVideoId(@PathVariable(name = "videoId") Long videoId);

    @PutMapping("/video/{videoId}/name")
    ResponseVO <Void> updateVideoNameByVideoId(@PathVariable(name = "videoId") Long videoId,
                                               @RequestParam(name = "videoName") String videoName);

    @PutMapping("/video/{videoId}/cover")
    ResponseVO <Void> updateVideoCoverByVideoId(@PathVariable(name = "videoId") Long videoId,
                                                @RequestParam(name = "videoCover") String videoCover);

    @PutMapping("/user/{userId}/nickName")
    ResponseVO <Void> updateNickNameByUserId(@PathVariable(name = "userId") Long userId,
                                             @RequestParam(name = "nickName") String nickName);

    @PutMapping("/user/{userId}/avatar")
    ResponseVO <Void> updateAvatarByUserId(@PathVariable(name = "userId") Long userId,
                                           @RequestParam(name = "avatar") String avatar);

    @GetMapping("/statistics/daily")
    ResponseVO <List <CommentDailyStatisticsDTO>> getDailyCommentStatistics(
            @RequestParam(name = "statisticsDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate statisticsDate);
}
