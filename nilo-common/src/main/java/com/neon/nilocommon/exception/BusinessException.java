package com.neon.nilocommon.exception;


import com.neon.nilocommon.entity.enums.ResponseCodeEnum;
import lombok.Getter;

/**
 * 业务异常类
 */
public class BusinessException extends RuntimeException
{

    @Getter
    private ResponseCodeEnum codeEnum;
    @Getter
    private Integer code;

    private String message;

    /**
     * 使用异常消息来构建业务异常
     * @param message 异常消息
     */
    public BusinessException(String message)
    {
        super(message); // 这一步一定要把消息传给父类构造器，如果不传给父类构造器，日志记录异常的时候由于调用父类的getMessage()会导致记录不到异常消息
        this.message = message;
    }

    public BusinessException(Integer code, String message)
    {
        super(message);
        this.code = code;
        this.message = message;
    }

    public BusinessException(String message, Throwable e)
    {
        super(message, e);
        this.message = message;
    }

    public BusinessException(Throwable e)
    {
        super(e);
    }

    public BusinessException(ResponseCodeEnum codeEnum)
    {
        super(codeEnum.getMsg());
        this.codeEnum = codeEnum;
        this.code = codeEnum.getCode();
        this.message = codeEnum.getMsg();
    }

    @Override
    public String getMessage()
    {
        return message;
    }

    /**
     * 重写fillInStackTrace 业务异常不需要堆栈信息，<em>大幅度提高效率</em>
     */
    @Override
    public Throwable fillInStackTrace()
    {
        return this;
    }

}
