package com.neon.nilocommon.entity.po.redis;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class HeartbeatMessage
{
    private String videoId;

    private String fileIndex;

    private String sessionId;

    private String timestamp;
}
