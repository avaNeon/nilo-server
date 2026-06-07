package com.neon.nilomqconsumer.repository.elasticsearch;

import java.util.Map;

public interface VideoInfoDocExtensionRepository
{
    int increasePlayCountByVideoId(Map <Long, Integer> playCountMap);
}
