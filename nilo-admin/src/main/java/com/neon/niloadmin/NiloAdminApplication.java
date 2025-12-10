package com.neon.niloadmin;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@Import(com.neon.nilocommon.captcha.RedisCaptcha.class)
@MapperScan("com.neon.niloadmin.mapper")
@EnableScheduling
@EnableTransactionManagement
@SpringBootApplication
public class NiloAdminApplication
{

    public static void main(String[] args)
    {
        SpringApplication.run(NiloAdminApplication.class, args);
    }

}
