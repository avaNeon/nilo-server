package com.neon.nilomqconsumer.feign.comment;

import com.neon.nilocommon.entity.dto.UserSnapshotDTO;
import com.neon.nilocommon.entity.dto.VideoSnapshotDTO;
import com.neon.nilocommon.entity.dto.comment.CommentDailyStatisticsDTO;
import com.neon.nilocommon.entity.vo.ResponseVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
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

    @PutMapping("/replica/video")
    ResponseVO <Void> upsertVideoReplica(@RequestBody VideoSnapshotDTO video);

    @PutMapping("/replica/user")
    ResponseVO <Void> upsertUserReplica(@RequestBody UserSnapshotDTO user);

    @DeleteMapping("/replica/video/{videoId}")
    ResponseVO <Void> deleteVideoReplica(@PathVariable(name = "videoId") Long videoId);

    @DeleteMapping("/replica/user/{userId}")
    ResponseVO <Void> deleteUserReplica(@PathVariable(name = "userId") Long userId);

    @GetMapping("/statistics/daily")
    ResponseVO <List <CommentDailyStatisticsDTO>> getDailyCommentStatistics(
            @RequestParam(name = "statisticsDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate statisticsDate);
}
