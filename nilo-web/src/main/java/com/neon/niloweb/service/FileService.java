package com.neon.niloweb.service;


import com.neon.nilocommon.entity.constants.Constants;
import com.neon.nilocommon.exception.BusinessException;
import com.neon.nilocommon.util.StringUtil;
import com.neon.niloweb.config.WebConfig;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;


@RequiredArgsConstructor
@Service
public class FileService
{
    private final WebConfig webConfig;

    public void downloadImage(HttpServletResponse response, String fileName)
    {
        if (!StringUtil.isValidPath(fileName)) throw new BusinessException("非法的文件路径");
        String suffix = StringUtil.getSuffix(fileName);
        response.setContentType("image/" + suffix.replace(".", ""));
        response.setHeader("Cache-Control", "max-age=2592000"); // 一年
        readFile(response, fileName);
    }

    /**
     * 从系统中读取文件，写出到response中
     */
    private void readFile(HttpServletResponse response, String fileName)
    {
        File file = new File(webConfig.getRootFilePath() + "/" + Constants.FILE_FOLDER_NAME + "/" + fileName);
        if (!file.exists()) return;
        try (ServletOutputStream outputStream = response.getOutputStream() ; FileInputStream inputStream = new FileInputStream(file))
        {
            byte[] bytes = new byte[1024];
            int len = 0;
            while ((len = inputStream.read(bytes)) != -1)
            {
                outputStream.write(bytes, 0, len);
            }
            outputStream.flush();
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
    }
}
