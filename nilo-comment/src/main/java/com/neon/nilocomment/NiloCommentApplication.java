package com.neon.nilocomment;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@EnableFeignClients
@MapperScan("com.neon.nilocomment.mapper")
@SpringBootApplication
public class NiloCommentApplication
{

    public static void main(String[] args)
    {
        SpringApplication.run(NiloCommentApplication.class, args);
    }

}
