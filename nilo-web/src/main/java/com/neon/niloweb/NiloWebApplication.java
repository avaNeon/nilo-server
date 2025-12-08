package com.neon.niloweb;

import com.neon.nilocommon.captcha.RedisCaptcha;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

@Import(RedisCaptcha.class)
@MapperScan("com.neon.niloweb.mapper")
@SpringBootApplication
public class NiloWebApplication
{

    public static void main(String[] args)
    {
        SpringApplication.run(NiloWebApplication.class, args);
    }

}
