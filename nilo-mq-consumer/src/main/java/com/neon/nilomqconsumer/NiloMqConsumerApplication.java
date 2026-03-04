package com.neon.nilomqconsumer;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@MapperScan("com.neon.nilomqconsumer.mapper")
@SpringBootApplication
public class NiloMqConsumerApplication
{

    public static void main(String[] args)
    {
        SpringApplication.run(NiloMqConsumerApplication.class, args);
    }

}
