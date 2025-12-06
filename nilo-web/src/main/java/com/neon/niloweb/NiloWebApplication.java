package com.neon.niloweb;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;


@MapperScan("com.neon.niloweb.mapper")
@SpringBootApplication
public class NiloWebApplication
{

    public static void main(String[] args)
    {
        SpringApplication.run(NiloWebApplication.class, args);
    }

}
