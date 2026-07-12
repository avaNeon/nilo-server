package com.neon.niloweb.util;

import org.springframework.boot.system.ApplicationHome;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

/**
 * 用于获取项目相对路径的工具类
 */
@Component
public class PathResolver
{
    private final Path baseDir;

    public PathResolver()
    {
        ApplicationHome home = new ApplicationHome(getClass());
        this.baseDir = home.getDir().toPath().toAbsolutePath().normalize();
    }

    public Path resolve(String relativeSubPathStr)
    {
        return baseDir.resolve(relativeSubPathStr).normalize();
    }
}