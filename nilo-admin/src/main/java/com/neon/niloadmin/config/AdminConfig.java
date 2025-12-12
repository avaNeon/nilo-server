package com.neon.niloadmin.config;

import com.neon.nilocommon.entity.po.Admin;
import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@ConfigurationProperties(prefix = "admin")
@Configuration
public class AdminConfig
{
    private Admin[] admins;

    @Value("${project.folder}")
    private String rootFilePath;
}
