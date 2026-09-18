package com.neon.niloai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@EnableFeignClients
@SpringBootApplication
public class NiloAiApplication
{

    public static void main(String[] args)
    {
        SpringApplication.run(NiloAiApplication.class, args);
    }

}
