package com.neon.nilocommon.util;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.stereotype.Component;

/**
 * 异常日志工具类
 */
@Slf4j
@Component
public class ExceptionLog
{
    public void trace(Throwable e)
    {
        ExceptionInfo info = logException(e);
        log.trace("Exception Occurred -> [Reason]: [{}], [Location]: [{}]", info.message(), info.location());
    }

    public void debug(Throwable e)
    {
        ExceptionInfo info = logException(e);
        log.debug("Exception Occurred -> [Reason]: [{}], [Location]: [{}]", info.message(), info.location());
    }

    public void info(Throwable e)
    {
        ExceptionInfo info = logException(e);
        log.info("Exception Occurred -> [Reason]: [{}], [Location]: [{}]", info.message(), info.location());
    }

    public void warn(Throwable e)
    {
        ExceptionInfo info = logException(e);
        log.warn("Exception Occurred -> [Reason]: [{}], [Location]: [{}]", info.message(), info.location());
    }

    public void error(Throwable e)
    {
        ExceptionInfo info = logException(e);
        log.error("Exception Occurred -> [Reason]: [{}], [Location]: [{}]", info.message(), info.location());
    }

    /**
     * 记录异常日志
     *
     * @param e 异常
     */
    private ExceptionInfo logException(Throwable e)
    {
        Throwable rootCause = ExceptionUtils.getRootCause(e);
        if (rootCause == null)
        {
            rootCause = e;
        }

        String message = ExceptionUtils.getRootCauseMessage(e);

        String location = "Unknown Location";
        StackTraceElement[] stackTrace = rootCause.getStackTrace();
        if (stackTrace.length > 0)
        {
            StackTraceElement firstStackTrace = stackTrace[0];
            location = String.format("%s.%s(Line:%d)",
                                     firstStackTrace.getClassName(),
                                     firstStackTrace.getMethodName(),
                                     firstStackTrace.getLineNumber());
        }

        return new ExceptionInfo(message, location);


    }

    private record ExceptionInfo(String message, String location)
    {
    }
}
