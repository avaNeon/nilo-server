package com.neon.nilocommon.util;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 异常日志工具类
 */
@Slf4j
@Component
public class ExceptionLog
{
    private static final String APPLICATION_PACKAGE_PREFIX = "com.neon.";

    private static final int MAX_STACK_DEPTH = 12;

    public void trace(Throwable e)
    {
        ExceptionInfo info = logException(e);
        log.trace("Exception Occurred -> {}", info.detail());
    }

    public void debug(Throwable e)
    {
        ExceptionInfo info = logException(e);
        log.debug("Exception Occurred -> {}", info.detail());
    }

    public void info(Throwable e)
    {
        ExceptionInfo info = logException(e);
        log.info("Exception Occurred -> {}", info.detail());
    }

    public void warn(Throwable e)
    {
        ExceptionInfo info = logException(e);
        log.warn("Exception Occurred -> {}", info.detail());
    }

    public void error(Throwable e)
    {
        ExceptionInfo info = logException(e);
        log.error("Exception Occurred -> {}", info.detail());
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

        String message = formatException(rootCause);
        String direct = e == rootCause ? null : formatException(e);
        String location = formatStackTraceElement(findApplicationFrame(rootCause));
        String rootLocation = formatStackTraceElement(firstFrame(rootCause));
        List <String> stackTrace = formatUsefulStackTrace(rootCause);

        StringBuilder detail = new StringBuilder();
        detail.append("[Reason]: [").append(message).append("]");
        if (direct != null)
        {
            detail.append(", [Thrown]: [").append(direct).append("]");
        }
        detail.append(", [Location]: [").append(location).append("]");
        detail.append(", [RootLocation]: [").append(rootLocation).append("]");
        if (!stackTrace.isEmpty())
        {
            detail.append(", [Stack]: [").append(String.join(" <- ", stackTrace)).append("]");
        }

        return new ExceptionInfo(detail.toString());
    }

    private String formatException(Throwable e)
    {
        String message = e.getMessage();
        return e.getClass().getSimpleName() + (message == null ? "" : ": " + message);
    }

    private StackTraceElement firstFrame(Throwable e)
    {
        StackTraceElement[] stackTrace = e.getStackTrace();
        return stackTrace.length == 0 ? null : stackTrace[0];
    }

    private StackTraceElement findApplicationFrame(Throwable e)
    {
        for (StackTraceElement element : e.getStackTrace())
        {
            if (element.getClassName().startsWith(APPLICATION_PACKAGE_PREFIX))
            {
                return element;
            }
        }
        return firstFrame(e);
    }

    private List <String> formatUsefulStackTrace(Throwable e)
    {
        List <String> usefulStackTrace = new ArrayList <>();
        boolean previousWasSkipped = false;

        for (StackTraceElement element : e.getStackTrace())
        {
            if (isUsefulFrame(element))
            {
                usefulStackTrace.add(formatStackTraceElement(element));
                previousWasSkipped = false;
            }
            else if (!previousWasSkipped && !usefulStackTrace.isEmpty())
            {
                usefulStackTrace.add("...");
                previousWasSkipped = true;
            }

            if (usefulStackTrace.size() >= MAX_STACK_DEPTH)
            {
                usefulStackTrace.add("...");
                break;
            }
        }

        return usefulStackTrace;
    }

    private boolean isUsefulFrame(StackTraceElement element)
    {
        String className = element.getClassName();
        return className.startsWith(APPLICATION_PACKAGE_PREFIX) || className.startsWith("org.springframework.data") || className.startsWith(
                "org.springframework.beans") || className.startsWith("org.springframework.transaction");
    }

    private String formatStackTraceElement(StackTraceElement element)
    {
        if (element == null)
        {
            return "Unknown Location";
        }
        return String.format("%s.%s(Line:%d)", element.getClassName(), element.getMethodName(), element.getLineNumber());
    }

    private record ExceptionInfo(String detail)
    {
    }
}
