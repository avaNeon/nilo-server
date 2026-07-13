package com.neon.nilocommon.util;

import com.neon.nilocommon.entity.constants.MinioKey;
import com.neon.nilocommon.entity.constants.RedisKey;
import com.neon.nilocommon.entity.constants.VideoResolution;
import com.neon.nilocommon.entity.enums.ResponseCode;
import com.neon.nilocommon.exception.BusinessException;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

/**
 * 文件处理工具类
 */
@Slf4j
public class FileUtil
{
    private static final Pattern IMAGE_KEY_PATTERN = Pattern.compile(
            "^\\d{8}/[A-Za-z0-9]{30}(?:_thumb)?\\.(jpg|jpeg|png|gif|bmp|webp|avif|svg)$",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern VIDEO_HLS_OBJECT_KEY_PATTERN = Pattern.compile(
            "^(pending|public)/\\d{8}/[A-Za-z0-9]{30}/(?:master\\.m3u8|(?:720P|480P)/(?:index\\.m3u8|tsFolder/\\d{4}\\.ts))$",
            Pattern.CASE_INSENSITIVE);

    /**
     * 校验不带前缀的图片 plain key 是否合法
     */
    public static boolean isValidImagePlainKey(String plainKey)
    {
        if (plainKey == null)
        {
            return false;
        }
        return IMAGE_KEY_PATTERN.matcher(plainKey).matches();
    }

    /**
     * 校验带 tmp/pending/public 前缀的图片 MinIO object key 是否合法
     */
    public static boolean isValidPrefixedImageObjectKey(String key)
    {
        if (key == null)
        {
            return false;
        }

        if (key.startsWith(MinioKey.PUBLIC_PREFIX))
        {
            return isValidImagePlainKey(key.substring(MinioKey.PUBLIC_PREFIX.length()));
        }
        if (key.startsWith(MinioKey.PENDING_PREFIX))
        {
            return isValidImagePlainKey(key.substring(MinioKey.PENDING_PREFIX.length()));
        }
        if (key.startsWith(MinioKey.TMP_PREFIX))
        {
            return isValidImagePlainKey(key.substring(MinioKey.TMP_PREFIX.length()));
        }

        return isValidImagePlainKey(key);
    }

    /**
     * 校验 pending/public 前缀下的 HLS 视频 object key 是否合法
     */
    public static boolean isValidVideoHlsObjectKey(String key)
    {
        if (key == null)
        {
            return false;
        }
        return VIDEO_HLS_OBJECT_KEY_PATTERN.matcher(key).matches();
    }

    /**
     * 构造缩略图名称
     *
     * @param fileName 图片文件名称
     * @return 缩略图文件名称
     */
    public static String constructThumbnailName(String fileName)
    {
        String suffix = StringUtil.getSuffix(fileName);
        return fileName.substring(0, fileName.lastIndexOf(".")) + RedisKey.THUMBNAIL_SUFFIX + suffix;
    }

    /**
     * 根据图片 Content-Type 返回带点的文件后缀<hr/>
     * 用于将用户上传文件名中的假后缀纠正为真实格式后缀
     *
     * @param contentType 图片 Content-Type（如 image/jpeg）
     * @return 带点的后缀（如 .jpg）；无法识别时返回 null
     */
    public static String getImageSuffixByContentType(String contentType)
    {
        if (contentType == null)
        {
            return null;
        }

        return switch (contentType)
        {
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/gif" -> ".gif";
            case "image/bmp" -> ".bmp";
            case "image/webp" -> ".webp";
            case "image/avif" -> ".avif";
            case "image/svg+xml" -> ".svg";
            default -> null;
        };
    }

    /**
     * 通过文件头魔数校验是否为图片，并返回对应的 Content-Type<hr/>
     * 仅读取文件头部少量字节，性能开销极低，能够可靠识别文件真实类型（防止伪造 Content-Type）<br/>
     * 支持的格式：JPEG、PNG、GIF、BMP、WebP、AVIF、SVG
     *
     * @param bytes 文件头部字节
     * @param len   有效字节长度
     * @return 图片对应的 Content-Type，非图片返回 null
     */
    public static String validateImageType(byte[] bytes, int len)
    {
        if (bytes == null || len < 4) return null;

        // JPEG: FF D8 FF
        if ((bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xD8 && (bytes[2] & 0xFF) == 0xFF)
        {
            return "image/jpeg";
        }

        // PNG: 89 50 4E 47
        if (bytes[0] == (byte) 0x89 && bytes[1] == 0x50 && bytes[2] == 0x4E && bytes[3] == 0x47)
        {
            return "image/png";
        }

        // GIF: 47 49 46 38 (GIF8)
        if (bytes[0] == 0x47 && bytes[1] == 0x49 && bytes[2] == 0x46 && bytes[3] == 0x38)
        {
            return "image/gif";
        }

        // BMP: 42 4D (BM)
        if (bytes[0] == 0x42 && bytes[1] == 0x4D)
        {
            return "image/bmp";
        }

        // WebP: 52 49 46 46 ... 57 45 42 50 (RIFF .... WEBP)
        if (bytes[0] == 0x52 && bytes[1] == 0x49 && bytes[2] == 0x46 && bytes[3] == 0x46 && len >= 12 && bytes[8] == 0x57 && bytes[9] == 0x45 && bytes[10] == 0x42 && bytes[11] == 0x50)
        {
            return "image/webp";
        }
        // AVIF: xx xx xx xx 66 74 79 70 61 76 69 66 (ftyp box with avif brand)
        if (len >= 12 && bytes[4] == 0x66 && bytes[5] == 0x74 && bytes[6] == 0x79 && bytes[7] == 0x70 && bytes[8] == 0x61 && bytes[9] == 0x76 && bytes[10] == 0x69 && bytes[11] == 0x66)
        {
            return "image/avif";
        }

        // SVG: 文本格式，跳过 BOM 和空白后检查是否以 <svg 或 <?xml 开头
        String head = new String(bytes, 0, len, StandardCharsets.UTF_8).stripLeading();
        if (head.startsWith("<svg") || head.startsWith("<?xml") || head.startsWith("<!DOCTYPE svg"))
        {
            return "image/svg+xml";
        }

        return null;
    }

    /**
     * 校验上传文件是否为真实图片，校验通过后返回对应的 Content-Type<hr/>
     * 校验文件头魔数，防止伪造 Content-Type 上传恶意文件
     *
     * @param file 上传的文件
     * @return 图片对应的 Content-Type
     */
    public static String validateImageFile(MultipartFile file)
    {
        byte[] header = new byte[512];

        try (InputStream in = file.getInputStream())
        {
            int totalRead = 0;

            while (totalRead < header.length)
            {
                int read = in.read(header, totalRead, header.length - totalRead);

                if (read == -1) break;

                totalRead += read;
            }

            String contentType = validateImageType(header, totalRead);
            if (contentType == null)
            {
                throw new BusinessException(ResponseCode.INVALID_ARGUMENTS);
            }
            return contentType;
        }
        catch (IOException e)
        {
            throw new RuntimeException("文件读取失败", e);
        }
    }

    /**
     * 校验并规范化清晰度文件夹名（720P / 480P）
     */
    public static String resolveResolutionFolder(String folderName)
    {
        VideoResolution vr = VideoResolution.fromFolderName(folderName);
        if (vr == null)
        {
            throw new BusinessException(ResponseCode.INVALID_ARGUMENTS);
        }
        return vr.getFolderName();
    }

    /**
     * 将 m3u8 文本内容写入 HTTP 响应
     *
     * @param response HttpServletResponse
     * @param content m3u8 文本内容
     */
    public static void writeM3u8Response(HttpServletResponse response, String content)
    {
        response.setContentType("application/vnd.apple.mpegurl;charset=UTF-8");
        response.setHeader("Content-Disposition", "inline; filename=\"playlist.m3u8\"");
        try
        {
            response.getWriter().write(content);
            response.getWriter().flush();
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
    }
}
