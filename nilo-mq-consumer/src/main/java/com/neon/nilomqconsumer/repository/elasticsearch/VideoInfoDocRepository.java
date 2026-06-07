package com.neon.nilomqconsumer.repository.elasticsearch;

import com.neon.nilocommon.entity.po.document.VideoInfoDoc;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface VideoInfoDocRepository extends ElasticsearchRepository <VideoInfoDoc, Long>, VideoInfoDocExtensionRepository
{
}
