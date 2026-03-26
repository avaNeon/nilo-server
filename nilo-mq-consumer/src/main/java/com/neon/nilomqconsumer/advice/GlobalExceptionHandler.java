package com.neon.nilomqconsumer.advice;


import com.neon.nilocommon.entity.enums.ResponseCode;
import com.neon.nilocommon.entity.vo.ResponseVO;
import com.neon.nilocommon.util.ExceptionLog;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import static com.neon.nilocommon.entity.vo.ResponseVO.STATUS_ERROR;


/**
 * HTTP全局异常处理器
 */
@RequiredArgsConstructor
@RestControllerAdvice
public class GlobalExceptionHandler
{
    private final ExceptionLog log;

    /**
     * 兜底异常处理
     */
    @ExceptionHandler(Exception.class)
    ResponseVO <Object> handleException(Exception e, HttpServletRequest request)
    {
        log.error(e); // 糟糕，出现了意想不到的错误！
        ResponseVO <Object> response = new ResponseVO <>();
        response.setCode(ResponseCode.SERVER_ERROR.getCode());
        response.setInfo(ResponseCode.SERVER_ERROR.getMsg());
        response.setStatus(STATUS_ERROR);
        return response;
    }

}
