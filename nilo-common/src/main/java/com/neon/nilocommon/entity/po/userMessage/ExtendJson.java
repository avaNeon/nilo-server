package com.neon.nilocommon.entity.po.userMessage;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ExtendJson
{
    /**
     * 记录消息的主要内容
     */
    private String mainContent;

    /**
     * 记录消息的次要内容
     */
    private String subContent;
}
