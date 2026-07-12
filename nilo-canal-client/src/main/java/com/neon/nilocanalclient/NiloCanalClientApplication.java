package com.neon.nilocanalclient;

import com.neon.nilocanalclient.config.CanalProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;


@EnableConfigurationProperties(CanalProperties.class)
@SpringBootApplication
public class NiloCanalClientApplication
{
    public static void main(String[] args)
    {
        SpringApplication.run(NiloCanalClientApplication.class, args);
    }
}
