package com.neon.niloadmin.repository.elasticsearch;

import com.neon.nilocommon.entity.po.document.VideoInfoDoc;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface VideoInfoDocRepository extends ElasticsearchRepository <VideoInfoDoc, Long>, VideoInfoDocExtensionRepository
{
    /**
     * 根据视频标题分词查询记录
     *
     * @param videoName 视频标题
     * @return 视频记录
     */
    List <VideoInfoDoc> findVideoInfoDocsByVideoName(String videoName);

    /**
     * 根据 videoId 找到对应 document
     * @param videoId 视频ID
     * @return document
     */
    VideoInfoDoc findVideoInfoDocByVideoId(Long videoId);

}
