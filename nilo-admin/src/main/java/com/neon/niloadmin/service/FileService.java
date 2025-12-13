package com.neon.niloadmin.service;

import com.neon.niloadmin.config.AdminConfig;
import com.neon.nilocommon.entity.constants.Constants;
import com.neon.nilocommon.entity.constants.DatePattern;
import com.neon.nilocommon.exception.BusinessException;
import com.neon.nilocommon.util.FFmpegUtil;
import com.neon.nilocommon.util.StringUtil;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.RandomStringUtils;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;


@RequiredArgsConstructor
@Service
public class FileService
{
    private final AdminConfig adminConfig;

    public String uploadImage(MultipartFile file, Boolean createThumbnail)
    {
        String dateName = LocalDate.now().format(DateTimeFormatter.ofPattern(DatePattern.YYYYMM)); // 目录按月划分
        String folderPath = adminConfig.getRootFilePath() + "/" + Constants.FILE_FOLDER_NAME + "/" + Constants.COVER_FOLDER_NAME + "/" + dateName;

        File folderFile = new File(folderPath);
        if (!folderFile.exists()) folderFile.mkdirs();

        String fileName = file.getOriginalFilename();
        String suffix = StringUtil.getSuffix(fileName);
        String savedFileName = RandomStringUtils.randomAlphanumeric(30) + suffix;

        String filePath = folderPath + "/" + savedFileName; // 将文件拷贝至指定位置
        try
        {
            file.transferTo(new File(filePath));
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }

        // 生成缩略图
        if (createThumbnail)
        {
            FFmpegUtil.creatImgThumbnail(filePath, adminConfig.isShowCommandLogs());
        }

        return Constants.COVER_FOLDER_NAME + "/" + dateName + "/" + savedFileName;
    }

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
        File file = new File(adminConfig.getRootFilePath() + "/" + Constants.FILE_FOLDER_NAME + "/" + fileName);
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
