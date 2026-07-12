package com.neon.nilomqconsumer;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@EnableFeignClients
@MapperScan("com.neon.nilomqconsumer.mapper")
@SpringBootApplication
public class NiloMqConsumerApplication
{

    public static void main(String[] args)
    {
        SpringApplication.run(NiloMqConsumerApplication.class, args);
    }

}
