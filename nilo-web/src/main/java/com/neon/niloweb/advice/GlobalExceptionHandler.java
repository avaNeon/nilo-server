package com.neon.niloweb.advice;


import com.neon.nilocommon.entity.enums.ResponseCodeEnum;
import com.neon.nilocommon.entity.vo.ResponseVO;
import com.neon.nilocommon.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.util.List;

import static com.neon.nilocommon.entity.vo.ResponseVO.STATUS_ERROR;


/**
 * 全局异常处理器
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler
{

    /**
     * 没有对应接口异常处理
     */
    @ExceptionHandler(NoHandlerFoundException.class)
    ResponseVO <Object> handleNoHandlerException(NoHandlerFoundException e, HttpServletRequest request)
    {
        log.error("请求错误，请求地址{},错误信息:", request.getRequestURL(), e);
        ResponseVO <Object> response = new ResponseVO <>();
        response.setCode(ResponseCodeEnum.NOT_FOUND.getCode());
        response.setInfo(ResponseCodeEnum.NOT_FOUND.getMsg());
        response.setStatus(STATUS_ERROR);
        return response;
    }

    /**
     * 参数不合法异常处理
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseVO <Object> handleInvalidException(MethodArgumentNotValidException e, HttpServletRequest request)
    {
        log.error("请求错误，请求地址{},错误信息:", request.getRequestURL(), e);
        ResponseVO <Object> response = new ResponseVO <>();
        response.setCode(ResponseCodeEnum.INVALID_ARGUMENTS.getCode());
        response.setInfo(ResponseCodeEnum.INVALID_ARGUMENTS.getMsg());
        response.setStatus(STATUS_ERROR);
        // 将我们在校验注解中设置的message放到response的data中，显示给前端
        List <String> messageList = e.getBindingResult()
                                     .getAllErrors()
                                     .stream()
                                     .map(DefaultMessageSourceResolvable::getDefaultMessage)
                                     .toList();
        response.setData(messageList);
        return response;
    }

    /**
     * 业务异常处理
     */
    @ExceptionHandler(BusinessException.class)
    ResponseVO <Object> handleBusinessException(BusinessException e, HttpServletRequest request)
    {
        log.error("请求错误，请求地址{},错误信息:", request.getRequestURL(), e);
        ResponseVO <Object> response = new ResponseVO <>();
        response.setCode(e.getCode() == null ? ResponseCodeEnum.UNKNOWN_ERROR.getCode() : e.getCode());
        response.setInfo(e.getMessage());
        response.setStatus(STATUS_ERROR);
        return response;
    }

    /**
     * 参数错误异常处理
     */
    @ExceptionHandler({BindException.class, MethodArgumentTypeMismatchException.class})
    ResponseVO <Object> handleArgsException(Exception e, HttpServletRequest request)
    {
        log.error("请求错误，请求地址{},错误信息:", request.getRequestURL(), e);
        ResponseVO <Object> response = new ResponseVO <>();
        response.setCode(ResponseCodeEnum.WRONG_ARGUMENTS.getCode());
        response.setInfo(ResponseCodeEnum.WRONG_ARGUMENTS.getMsg());
        response.setStatus(STATUS_ERROR);
        return response;
    }

    /**
     * 唯一键重复异常处理
     */
    @ExceptionHandler(DuplicateKeyException.class)
    ResponseVO <Object> handleDuplicateKeyException(DuplicateKeyException e, HttpServletRequest request)
    {
        log.error("请求错误，请求地址{},错误信息:", request.getRequestURL(), e);
        ResponseVO <Object> response = new ResponseVO <>();
        response.setCode(ResponseCodeEnum.DATA_EXISTED.getCode());
        response.setInfo(ResponseCodeEnum.DATA_EXISTED.getMsg());
        response.setStatus(STATUS_ERROR);
        return response;
    }

    /**
     * 兜底异常处理
     */
    @ExceptionHandler(Exception.class)
    ResponseVO <Object> handleException(Exception e, HttpServletRequest request)
    {
        log.error("请求错误，请求地址{},错误信息:", request.getRequestURL(), e);
        ResponseVO <Object> response = new ResponseVO <>();
        response.setCode(ResponseCodeEnum.SERVER_ERROR.getCode());
        response.setInfo(ResponseCodeEnum.SERVER_ERROR.getMsg());
        response.setStatus(STATUS_ERROR);
        return response;
    }

}
