package com.neon.nilocommon.autoconfigure.serialization;

import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.annotation.Order;
import org.springframework.format.FormatterRegistry;
import org.springframework.format.datetime.standard.DateTimeFormatterRegistrar;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 统一JSON序列化规则<hr/>
 * 其中包括：<br/>
 *  1.当从URL和requestbody传入/发送日期的反序列化/序列化问题
 *  2.JSON序列化时如果没有接受类型的全部属性就报错的问题
 */
@RequiredArgsConstructor
@EnableConfigurationProperties(GlobalSerializationProperties.class)
@ConditionalOnProperty(prefix = "common.global.serialization", name = "enabled", havingValue = "true", matchIfMissing = true)
@AutoConfiguration
public class GlobalSerializationAutoConfiguration implements WebMvcConfigurer
{
    private final GlobalSerializationProperties properties;

    /**
     * 使时间通过GET请求传入时能正确解析
     */
    @Value("${spring.mvc.format.date-time:yyyy-MM-dd HH:mm:s}")
    private String paramPattern;

    /**
     * 添加LocalDateTime类型变量的序列化反序列化规则<br/>
     * 这样可以和其它Jackson2ObjectMapperBuilderCustomizer叠加
     *
     * @return 全局Jackson2ObjectMapperBuilderCustomizer
     */
    @Bean
    @Order(1) //SpringBoot默认的配置是0，这里我们给个1把默认配置覆盖掉
    public Jackson2ObjectMapperBuilderCustomizer jsonCustomizer()
    {
        return builder ->
        {
            //针对于LocalDateTime类的序列化反序列化规则
            builder.serializerByType(LocalDateTime.class, new LocalDateTimeSerializer(DateTimeFormatter.ofPattern(properties.pattern)));
            builder.deserializerByType(LocalDateTime.class, new LocalDateDeserializer(DateTimeFormatter.ofPattern(properties.pattern)));
            //将Long统一序列化为字符串，避免前端JS Number精度丢失（如雪花ID）
            builder.serializerByType(Long.class, ToStringSerializer.instance);
            builder.serializerByType(Long.TYPE, ToStringSerializer.instance);
            //设置JSON转化时没看到全部属性就失败是否开启的规则
            builder.failOnUnknownProperties(properties.jsonFailOnUnknownProperties);
        };
    }

    /**
     * 解决日期为URL参数时的转化问题
     */
    @Override
    public void addFormatters(FormatterRegistry registry)
    {
        DateTimeFormatterRegistrar registrar = new DateTimeFormatterRegistrar();
        registrar.setDateTimeFormatter(DateTimeFormatter.ofPattern(paramPattern));
        registrar.registerFormatters(registry);
    }
}
