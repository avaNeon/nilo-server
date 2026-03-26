package com.neon.nilocommon.autoconfigure.log;

import com.neon.nilocommon.util.ExceptionLog;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@EnableConfigurationProperties(ExceptionLogProperties.class)
@ConditionalOnClass({Logger.class, ExceptionUtils.class})
@ConditionalOnProperty(prefix = "common.log.exception", name = "enabled", havingValue = "true", matchIfMissing = true)
@AutoConfiguration
public class ExceptionLogAutoConfiguration
{
    @Bean
    @ConditionalOnMissingBean(ExceptionLog.class)
    public ExceptionLog exceptionLog()
    {
        return new ExceptionLog();
    }
}
