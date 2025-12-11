package com.neon.niloadmin.advice;


import com.neon.nilocommon.entity.enums.ResponseCode;
import com.neon.nilocommon.entity.vo.ResponseVO;
import com.neon.nilocommon.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

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
     * 业务异常处理
     */
    @ExceptionHandler(BusinessException.class)
    ResponseVO <Object> handleBusinessException(BusinessException e, HttpServletRequest request)
    {
        log.error("请求错误，请求地址{},错误信息:", request.getRequestURL(), e);
        ResponseVO <Object> response = new ResponseVO <>();
        response.setCode(e.getCode() == null ? ResponseCode.UNKNOWN_ERROR.getCode() : e.getCode());
        response.setInfo(e.getMessage());
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
        response.setCode(ResponseCode.DATA_EXISTED.getCode());
        response.setInfo(ResponseCode.DATA_EXISTED.getMsg());
        response.setStatus(STATUS_ERROR);
        return response;
    }

    /**
     * DTO参数不合法异常处理
     */
    @ExceptionHandler({MethodArgumentNotValidException.class,HandlerMethodValidationException.class})
    ResponseVO <Object> handleDTOInvalidException(MethodArgumentNotValidException e, HttpServletRequest request)
    {
        log.error("请求错误，请求地址{},错误信息:", request.getRequestURL(), e);
        ResponseVO <Object> response = new ResponseVO <>();
        response.setCode(ResponseCode.INVALID_ARGUMENTS.getCode());
        response.setInfo(ResponseCode.INVALID_ARGUMENTS.getMsg());
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
     * 非DTO参数不合法异常处理
     */
    @ExceptionHandler(ConstraintViolationException.class)
    ResponseVO <Object> handleArgsInvalidException(ConstraintViolationException e, HttpServletRequest request)
    {
        log.error("请求错误，请求地址{},错误信息:", request.getRequestURL(), e);
        ResponseVO <Object> response = new ResponseVO <>();
        response.setCode(ResponseCode.INVALID_ARGUMENTS.getCode());
        response.setInfo(ResponseCode.INVALID_ARGUMENTS.getMsg());
        response.setStatus(STATUS_ERROR);
        // 将我们在校验注解中设置的message放到response的data中，显示给前端
        List <String> messageList = e.getConstraintViolations().stream().map(ConstraintViolation::getMessage).toList();
        response.setData(messageList);
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
        response.setCode(ResponseCode.WRONG_ARGUMENTS.getCode());
        response.setInfo(ResponseCode.WRONG_ARGUMENTS.getMsg());
        response.setStatus(STATUS_ERROR);
        return response;
    }

    /**
     * 没有对应静态资源异常处理（也处理NoHandlerFoundException）
     */
    @ExceptionHandler(NoResourceFoundException.class)
    ResponseVO <Object> handleNoResourceFoundException(NoResourceFoundException e, HttpServletRequest request)
    {
        log.error("请求错误，请求地址{},错误信息:", request.getRequestURL(), e);
        ResponseVO <Object> response = new ResponseVO <>();
        response.setCode(ResponseCode.NOT_FOUND.getCode());
        response.setInfo(ResponseCode.NOT_FOUND.getMsg());
        response.setStatus(STATUS_ERROR);
        response.setData("没有对应的静态资源（也可能是没有对应的接口），请检查请求URI");
        return response;
    }

    /**
     * 没有对应接口异常处理<hr/>
     * 目前不被使用
     */
    @ExceptionHandler(NoHandlerFoundException.class)
    ResponseVO <Object> handleNoHandlerException(NoHandlerFoundException e, HttpServletRequest request)
    {
        log.error("请求错误，请求地址{},错误信息:", request.getRequestURL(), e);
        ResponseVO <Object> response = new ResponseVO <>();
        response.setCode(ResponseCode.NOT_FOUND.getCode());
        response.setInfo(ResponseCode.NOT_FOUND.getMsg());
        response.setStatus(STATUS_ERROR);
        response.setData("没有对应的接口，请检查请求URI");
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
        response.setCode(ResponseCode.SERVER_ERROR.getCode());
        response.setInfo(ResponseCode.SERVER_ERROR.getMsg());
        response.setStatus(STATUS_ERROR);
        return response;
    }

}
