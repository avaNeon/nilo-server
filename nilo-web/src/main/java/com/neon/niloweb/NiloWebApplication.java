package com.neon.niloweb;

import com.neon.nilocommon.captcha.RedisCaptcha;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@Import(RedisCaptcha.class)
@EnableScheduling
@EnableTransactionManagement
@MapperScan("com.neon.niloweb.mapper")
@SpringBootApplication
public class NiloWebApplication
{

    public static void main(String[] args)
    {
        SpringApplication.run(NiloWebApplication.class, args);
    }

}
