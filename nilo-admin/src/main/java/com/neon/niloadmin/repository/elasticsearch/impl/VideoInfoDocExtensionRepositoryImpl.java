package com.neon.niloadmin.repository.elasticsearch.impl;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.neon.niloadmin.repository.elasticsearch.VideoInfoDocExtensionRepository;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class VideoInfoDocExtensionRepositoryImpl implements VideoInfoDocExtensionRepository
{
    private final ElasticsearchClient esClient;

}
