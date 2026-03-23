package com.neon.nilocommon.util;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;

import static com.neon.nilocommon.entity.constants.Constants.MAX_FILENAME_LENGTH;
import static com.neon.nilocommon.entity.constants.Constants.MAX_PATH_LENGTH;

public class StringUtil
{

    /**
     * 校验文件路径是否合法且在指定根目录下
     *
     * @param pathStr  待检查的路径字符串 (可能包含 / 或 \)
     * @param rootPath 允许的根目录 (如 /data/app/tmp)
     * @return true 合法, false 非法
     */
    public static boolean isValidPath(String pathStr, String rootPath)
    {
        // 1. 基础非空校验
        if (pathStr == null || pathStr.isEmpty()) return false;
        if (rootPath == null || rootPath.isEmpty()) return false;

        // 2. 长度与非法字符校验 (拦截 Null Byte 攻击)
        if (pathStr.length() > MAX_PATH_LENGTH) return false;
        if (pathStr.indexOf('\0') != -1) return false;

        try
        {
            // 3. 统一分隔符处理：兼容 Windows 发来的路径
            // 在 Linux 下，'\' 是合法文件名字符，但在跨平台交互中，我们通常将其视为分隔符处理
            // 如果你的生产者可能在 Windows 上，这步是必须的
            String safePathStr = pathStr.replace("\\", "/");

            // 4. 转换为 Path 对象并“标准化” (核心步骤)
            // normalize() 会自动解析并消除 "../", "./" 等冗余路径
            Path targetPath = Paths.get(safePathStr).normalize();
            Path root = Paths.get(rootPath).normalize();

            // 5. 校验是否尝试跳出根目录 (使用 Path 对象的 startsWith)
            // 这是一个关键点：Path.startsWith() 比较的是路径“节点”，而不是字符串
            // 例如：Path("/data/u_bak").startsWith("/data/u") -> 返回 false (安全)
            // 而 String("/data/u_bak").startsWith("/data/u") -> 返回 true (危险!)
            if (!targetPath.startsWith(root))
            {
                return false;
            }

            // 6. 再次确认：必须是绝对路径，防止传入相对路径绕过
            // 如果传入 "a.txt"，拼接后可能合法，但建议强制要求绝对路径
            if (!targetPath.isAbsolute())
            {
                // 如果你的业务允许传相对路径，这里可以改为 targetPath = root.resolve(targetPath).normalize();
                // 但根据你的描述，MQ 传过来的应该是完整路径
                return false;
            }

            // 7. 检查每一层文件名的长度限制
            for (Path part : targetPath)
            {
                if (part.toString().length() > MAX_FILENAME_LENGTH)
                {
                    return false;
                }
            }

            return true;

        }
        catch (InvalidPathException | SecurityException e)
        {
            // 路径格式本身就是错误的（比如包含操作系统不允许的字符）
            return false;
        }
    }

    /**
     * 尝试获取字符串的扩展名（带"."）
     *
     * @return 带"."的扩展名，如果没有"."则报错
     */
    public static String getSuffix(String str)
    {
        if (str == null || str.lastIndexOf(".") == -1) return null;
        return str.substring(str.lastIndexOf("."));
    }
}
