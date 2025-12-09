package com.neon.niloadmin;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@Import(com.neon.nilocommon.captcha.RedisCaptcha.class)
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
