package com.neon.nilocommon.util;


import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

@Slf4j
public class ProcessUtil
{
    private static final String osName = System.getProperty("os.name").toLowerCase();

    public static String executeCommand(String cmd, Boolean showLog)
    {
        if (cmd == null || cmd.isBlank())
        {
            return null;
        }

        Runtime runtime = Runtime.getRuntime();
        Process process = null;

        try
        {
            //判断操作系统
            if (osName.contains("win"))
            {
                process = Runtime.getRuntime().exec(cmd);
            }
            else
            {
                process = Runtime.getRuntime().exec(new String[]{"/bin/sh", "-c", cmd});
            }

            // 执行ffmpeg指令
            // 取出输出流和错误流的信息
            // 注意：必须要取出ffmpeg在执行命令过程中产生的输出信息，如果不取的话当输出流信息填满jvm存储输出留信息的缓冲区时，线程就回阻塞住
            PrintStream errorStream = new PrintStream(process.getErrorStream());
            PrintStream inputStream = new PrintStream(process.getInputStream());
            errorStream.start();
            inputStream.start();

            // 等待ffmpeg命令执行完
            int exitCode = process.waitFor();

            // 获取执行结果字符串
            String result = errorStream.stringBuffer.append(inputStream.stringBuffer).append("\n").toString();

            // 输出执行的命令信息
            if (showLog)
            {
                log.info("执行命令{}结果{}", cmd, result);
            }

            // 之前这里不管退出码，命令找不到（比如 exec 格式写错、可执行文件在当前系统上不存在）也会静默"成功"返回，
            // 调用方拿着一个实际没生成的文件路径继续往下走，等到真正读文件的时候才炸出一个看起来毫不相关的 NoSuchFileException。
            // 命令失败必须在这里就暴露出来。
            if (exitCode != 0)
            {
                log.error("执行命令失败，cmd={}，exitCode={}，输出={}", cmd, exitCode, result);
                throw new RuntimeException("命令执行失败，exitCode=" + exitCode + "，cmd=" + cmd);
            }

            return result;
        }
        catch (Exception e)
        {
            log.error("执行命令失败cmd{}失败:{} ", cmd, e.getMessage());
            throw new RuntimeException("视频转换失败", e);
        }
        finally
        {
            if (null != process)
            {
                ProcessKiller ffmpegKiller = new ProcessKiller(process);
                runtime.addShutdownHook(ffmpegKiller);
            }
        }
    }

    /**
     * 在程序退出前结束已有的FFmpeg进程
     */
    private static class ProcessKiller extends Thread
    {
        private Process process;

        public ProcessKiller(Process process)
        {
            this.process = process;
        }

        @Override
        public void run()
        {
            this.process.destroy();
        }
    }


    /**
     * 用于取出ffmpeg线程执行过程中产生的各种输出和错误流的信息
     */
    static class PrintStream extends Thread
    {
        InputStream inputStream = null;
        BufferedReader bufferedReader = null;
        StringBuffer stringBuffer = new StringBuffer();

        public PrintStream(InputStream inputStream)
        {
            this.inputStream = inputStream;
        }

        @Override
        public void run()
        {
            try
            {
                if (null == inputStream) return;
                bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
                String line;
                while ((line = bufferedReader.readLine()) != null) stringBuffer.append(line);
            }
            catch (Exception e)
            {
                log.error("读取输入流出错了！错误信息：" + e.getMessage());
            }
            finally
            {
                try
                {
                    if (null != bufferedReader)
                    {
                        bufferedReader.close();
                    }
                    if (null != inputStream)
                    {
                        inputStream.close();
                    }
                }
                catch (IOException e)
                {
                    log.error("调用PrintStream读取输出流后，关闭流时出错！");
                }
            }
        }
    }
}
